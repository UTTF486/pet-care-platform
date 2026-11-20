package com.petcare.service;

import com.petcare.common.*;
import com.petcare.dto.AccountDTO;
import com.petcare.entity.User;
import com.petcare.mapper.UserMapper;
import com.petcare.util.TokenService;
import com.petcare.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户与认证服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * 注册：注册成功直接返回 Token，前端不用再跳一次登录页。
     */
    public LoginVO register(AccountDTO dto) {
        // 先查一次给出友好提示；真正的唯一性由 t_user.uk_username 唯一索引兜底
        if (userMapper.selectByUsername(dto.getUsername()) != null) {
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
        userMapper.insert(user);

        return buildLoginVO(user);
    }

    /**
     * 登录：校验账号 -> 校验状态 -> 校验密码 -> 签发 Token。
     */
    public LoginVO login(AccountDTO dto) {
        User user = userMapper.selectByUsername(dto.getUsername());
        // 账号不存在和密码错误返回同一句提示，避免被撞库枚举出有效账号
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }
        return buildLoginVO(user);
    }

    public void logout(String token) {
        tokenService.removeToken(token);
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
        int offset = (pageNum - 1) * pageSize;
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
    public void changeStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (user.getRole() != null && user.getRole() == 1) {
            throw new BusinessException("不能禁用管理员账号");
        }
        userMapper.updateStatus(id, status);
        if (status == 0) {
            tokenService.kickOut(id);   // 删除 Redis 里的用户信息 -> 下次请求即 401
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
}
