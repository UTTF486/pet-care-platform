package com.petcare.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.common.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 登录态管理：Token 存 Redis，天然支持多实例部署。
 * <p>
 * 不使用纯 JWT：JWT 签发后无法直接撤销，而禁用用户需要会话立即失效。
 * 服务端通过 Redis 保存登录状态，可主动删除并支持多实例共享。
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
        LoginUser user = getLoginUser(token);
        refresh(token, user);
    }

    /**
     * 使用拦截器已经解析出的用户续期，避免一次请求重复读取两次 Redis。
     */
    public void refresh(String token, LoginUser user) {
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] serializerToken = redisTemplate.getStringSerializer()
                    .serialize(TOKEN_KEY_PREFIX + token);
            connection.expire(serializerToken, EXPIRE_SECONDS);
            if (user != null) {
                byte[] userKey = redisTemplate.getStringSerializer()
                        .serialize(USER_KEY_PREFIX + user.getId());
                connection.expire(userKey, EXPIRE_SECONDS);
            }
            return null;
        });
    }

    /**
     * 退出当前设备，只删除当前 Token；强制用户下线由 kickOut 负责。
     */
    public void removeToken(String token) {
        redisTemplate.delete(TOKEN_KEY_PREFIX + token);
    }

    /**
     * 管理员禁用用户时调用，让该用户已登录的会话立刻失效。
     */
    public void kickOut(Long userId) {
        redisTemplate.delete(USER_KEY_PREFIX + userId);
    }
}
