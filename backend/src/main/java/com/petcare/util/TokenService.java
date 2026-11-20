package com.petcare.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.common.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 登录态管理：Token 存 Redis，天然支持多实例部署。
 * <p>
 * 为什么不用纯 JWT：
 * 1) JWT 签发后无法主动失效（踢人下线要额外维护黑名单）—— 后台"禁用用户"就必须能立刻生效；
 * 2) 用 Redis 存 userId，服务端可控，扩展成本低。
 * <p>
 * 设计成两个 key：
 * login:token:{token} -> userId      定位是哪个用户
 * login:user:{userId} -> LoginUser   用户信息，禁用用户时删这个 key 即刻踢下线
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenService {

    private static final String TOKEN_KEY_PREFIX = "login:token:";
    private static final String USER_KEY_PREFIX = "login:user:";
    /** 登录有效期 2 小时，每次请求续期，类似"滑动过期" */
    private static final long EXPIRE_SECONDS = 2 * 60 * 60L;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 登录成功后签发 Token。
     */
    public String createToken(LoginUser loginUser) {
        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            // 1. token -> userId
            redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token,
                    String.valueOf(loginUser.getId()), EXPIRE_SECONDS, TimeUnit.SECONDS);
            // 2. userId -> 用户信息
            redisTemplate.opsForValue().set(USER_KEY_PREFIX + loginUser.getId(),
                    objectMapper.writeValueAsString(loginUser), EXPIRE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("签发 token 失败", e);
            throw new IllegalStateException("登录失败，请重试");
        }
        return token;
    }

    /**
     * 根据 Token 取登录用户；返回 null 视为未登录。
     */
    public LoginUser getLoginUser(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String userId = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        if (userId == null) {
            return null;   // token 过期或未登录
        }
        String json = redisTemplate.opsForValue().get(USER_KEY_PREFIX + userId);
        if (json == null) {
            // 用户被禁用/删除时会把 user key 删掉，这里就会命中，实现"立即踢下线"
            return null;
        }
        try {
            return objectMapper.readValue(json, LoginUser.class);
        } catch (Exception e) {
            log.error("解析登录用户失败, json={}", json, e);
            return null;
        }
    }

    /**
     * 续期：用户一直活跃就不过期。
     */
    public void refresh(String token) {
        redisTemplate.expire(TOKEN_KEY_PREFIX + token, EXPIRE_SECONDS, TimeUnit.SECONDS);
        LoginUser user = getLoginUser(token);
        if (user != null) {
            redisTemplate.expire(USER_KEY_PREFIX + user.getId(), EXPIRE_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 退出登录 / 强制下线。
     */
    public void removeToken(String token) {
        String userId = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        redisTemplate.delete(TOKEN_KEY_PREFIX + token);
        if (userId != null) {
            redisTemplate.delete(USER_KEY_PREFIX + userId);
        }
    }

    /**
     * 管理员禁用用户时调用，让该用户已登录的会话立刻失效。
     */
    public void kickOut(Long userId) {
        redisTemplate.delete(USER_KEY_PREFIX + userId);
    }
}
