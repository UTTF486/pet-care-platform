package com.petcare.service;

import com.petcare.common.*;
import com.petcare.annotation.RateLimit;
import com.petcare.dto.AccountDTO;
import com.petcare.dto.PasswordChangeDTO;
import com.petcare.entity.User;
import com.petcare.mapper.UserMapper;
import com.petcare.util.PageUtil;
import com.petcare.util.TokenService;
import com.petcare.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 用户与认证服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final long LOGIN_FAILURE_WINDOW_MINUTES = 15;
    private static final String LOGIN_FAILURE_PREFIX = "auth:login:fail:";
    private static final String PASSWORD_FAILURE_PREFIX = "auth:password:fail:";
    private static final RedisScript<Long> FAILURE_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]); "
                    + "if current == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])); end; "
                    + "return current;",
            Long.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final StringRedisTemplate redisTemplate;

    /**
     * 注册：注册成功直接返回 Token，前端不用再跳一次登录页。
     */
    @RateLimit(key = "auth:register", count = 5, seconds = 600,
            message = "注册尝试太频繁了，请稍后再试", failOpen = false)
    public LoginVO register(AccountDTO dto) {
        // 先查一次给出友好提示；真正的唯一性由 t_user.uk_username 唯一索引兜底
        User existingUser = userMapper.selectByUsername(dto.getUsername());
        if (existingUser != null) {
            throw new BusinessException("该账号已被注册，换一个试试");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        // 密码加密存储，数据库泄露也拿不到明文
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setPhone(StringUtils.hasText(dto.getPhone()) ? dto.getPhone() : "");
        user.setAvatar("");
        user.setRole(0);       // 注册入口只允许普通用户，管理员账号由脚本/后台创建
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 注册接口不做隐式登录；若首次请求已落库但 Token 失败，请走正常登录接口。
            throw new BusinessException("该账号已被注册，换一个试试");
        }

        try {
            clearLoginFailures(user.getUsername());
        } catch (Exception e) {
            // 账号已经写入，不能因为清理统计失败把注册结果伪装成失败。
            log.warn("注册成功后清理登录失败计数失败", e);
        }
        try {
            return buildLoginVO(user);
        } catch (RuntimeException e) {
            log.error("注册已落库但签发 Token 失败, username={}", user.getUsername(), e);
            throw new BusinessException(ResultCode.ERROR,
                    "账号已创建，但自动登录失败，请稍后直接使用该账号登录");
        }
    }

    /**
     * 登录：校验账号 -> 校验状态 -> 校验密码 -> 签发 Token。
     */
    @RateLimit(key = "auth:login", count = 10, seconds = 60,
            message = "登录尝试太频繁了，请稍后再试", failOpen = false)
    public LoginVO login(AccountDTO dto) {
        String failureKey = loginFailureKey(dto.getUsername());
        String currentFailures = redisTemplate.opsForValue().get(failureKey);
        if (isFailureLimitReached(currentFailures)) {
            throw new BusinessException(ResultCode.RATE_LIMIT, "密码错误次数过多，请 15 分钟后再试");
        }

        User user = userMapper.selectByUsername(dto.getUsername());
        // 账号不存在和密码错误返回同一句提示，避免被撞库枚举出有效账号
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            Long failures = redisTemplate.execute(
                    FAILURE_SCRIPT,
                    Collections.singletonList(failureKey),
                    String.valueOf(TimeUnit.MINUTES.toSeconds(LOGIN_FAILURE_WINDOW_MINUTES)));
            if (failures != null && failures >= MAX_LOGIN_FAILURES) {
                throw new BusinessException(ResultCode.RATE_LIMIT, "密码错误次数过多，请 15 分钟后再试");
            }
            throw new BusinessException("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }
        redisTemplate.delete(failureKey);
        return buildLoginVO(user);
    }

    public void logout(String token) {
        tokenService.removeToken(token);
    }

    /**
     * 修改密码后统一撤销该用户的 Redis 登录态，避免旧设备继续使用。
     */
    @RateLimit(key = "auth:password:change", count = 5, seconds = 60,
            message = "修改密码尝试太频繁了，请稍后再试", failOpen = false)
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, PasswordChangeDTO dto) {
        String failureKey = PASSWORD_FAILURE_PREFIX + userId;
        if (isFailureLimitReached(redisTemplate.opsForValue().get(failureKey))) {
            throw new BusinessException(ResultCode.RATE_LIMIT, "当前密码错误次数过多，请 15 分钟后再试");
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() == 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不存在或已被禁用");
        }
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            recordFailure(failureKey);
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前密码不正确");
        }
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "新密码不能与当前密码相同");
        }
        if (userMapper.updatePassword(userId, passwordEncoder.encode(dto.getNewPassword())) == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 与数据库更新处于同一事务；Redis 删除失败会触发数据库回滚。
        tokenService.kickOut(userId);
        redisTemplate.delete(failureKey);
        clearLoginFailures(user.getUsername());
    }

    public User getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        return user;
    }

    // ---------------- 后台用户管理 ----------------

    public PageResult<User> pageUsers(String keyword, int pageNum, int pageSize) {
        pageNum = PageUtil.normalizePageNum(pageNum);
        pageSize = PageUtil.normalizePageSize(pageSize);
        int offset = PageUtil.offset(pageNum, pageSize);
        long total = userMapper.countPage(keyword);
        if (total == 0) {
            return PageResult.empty(pageNum, pageSize);
        }
        List<User> list = userMapper.selectPage(keyword, offset, pageSize);
        return PageResult.of(total, pageNum, pageSize, list);
    }

    /**
     * 启用/禁用用户。禁用后要立刻踢下线，否则已登录的会话还能继续用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态值只能是 0 或 1");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (user.getRole() != null && user.getRole() == 1) {
            throw new BusinessException("不能禁用管理员账号");
        }
        if (userMapper.updateStatus(id, status) == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (status == 0) {
            tokenService.kickOut(id);   // 删除 Redis 里的用户信息 -> 下次请求即 401
        } else {
            clearLoginFailures(user.getUsername());
        }
    }

    /**
     * 组装登录返回：签发 Token + 回填用户信息。
     * 注意 User.password 上有 @JsonIgnore，返回前端时不会带密码。
     */
    private LoginVO buildLoginVO(User user) {
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getNickname(), user.getRole());
        String token = tokenService.createToken(loginUser);
        return new LoginVO(token, user);
    }

    private boolean isFailureLimitReached(String failures) {
        if (!StringUtils.hasText(failures)) {
            return false;
        }
        try {
            return Long.parseLong(failures) >= MAX_LOGIN_FAILURES;
        } catch (NumberFormatException e) {
            log.warn("登录失败计数格式异常，本次按未锁定处理");
            return false;
        }
    }

    private String loginFailureKey(String username) {
        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        return LOGIN_FAILURE_PREFIX
                + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(normalizedUsername.getBytes(StandardCharsets.UTF_8));
    }

    private void clearLoginFailures(String username) {
        redisTemplate.delete(loginFailureKey(username));
    }

    private void recordFailure(String failureKey) {
        Long failures = redisTemplate.execute(
                FAILURE_SCRIPT,
                Collections.singletonList(failureKey),
                String.valueOf(TimeUnit.MINUTES.toSeconds(LOGIN_FAILURE_WINDOW_MINUTES)));
        if (failures != null && failures >= MAX_LOGIN_FAILURES) {
            throw new BusinessException(ResultCode.RATE_LIMIT, "当前密码错误次数过多，请 15 分钟后再试");
        }
    }

}
