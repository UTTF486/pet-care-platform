package com.petcare.aspect;

import com.petcare.annotation.RateLimit;
import com.petcare.common.BusinessException;
import com.petcare.common.ResultCode;
import com.petcare.common.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;

/**
 * 限流切面：固定时间窗口 + Redis 自增计数。
 * <p>
 * 逻辑：key = rate:{业务}:{用户或IP}
 * 第一次访问时 INCR 得到 1，顺便设置过期时间；超过阈值就抛 429。
 * <p>
 * 计数保存在 Redis，保证多实例部署下共享同一限流窗口。
 * INCR 与 EXPIRE 通过 Lua 脚本原子执行，避免计数 key 永久泄漏。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String KEY_PREFIX = "rate:";
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]); "
                    + "if current == 1 then "
                    + "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])); "
                    + "end; "
                    + "return current;",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String identity = resolveIdentity();
        String redisKey = KEY_PREFIX + rateLimit.key() + ":" + identity;

        Long current = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Collections.singletonList(redisKey),
                String.valueOf(rateLimit.seconds()));

        if (current != null && current > rateLimit.count()) {
            log.warn("触发限流, key={}, 当前次数={}", redisKey, current);
            throw new BusinessException(ResultCode.RATE_LIMIT, rateLimit.message());
        }

        return joinPoint.proceed();
    }

    /**
     * 优先按用户维度限流；未登录（理论上不会发生）降级按 IP。
     */
    private String resolveIdentity() {
        Long userId = UserContext.getUserId();
        if (userId != null) {
            return "u" + userId;
        }
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            return "ip" + request.getRemoteAddr();
        }
        return "anonymous";
    }
}
