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
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

/**
 * 限流切面：固定时间窗口 + Redis 自增计数。
 * <p>
 * 逻辑：key = rate:{业务}:{用户或IP}
 * 第一次访问时 INCR 得到 1，顺便设置过期时间；超过阈值就抛 429。
 * <p>
 * 为什么用 Redis 而不是本地 Map：应用多实例部署时，本地计数各算各的，拦不住。
 * 已知局限：INCR 与 EXPIRE 非原子，极端情况下 key 可能没有过期时间；
 * 生产可用 Lua 脚本合并成一次调用（面试可以主动提这一点，是加分项）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String KEY_PREFIX = "rate:";

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String identity = resolveIdentity();
        String redisKey = KEY_PREFIX + rateLimit.key() + ":" + identity;

        Long current = redisTemplate.opsForValue().increment(redisKey);
        // 第一次访问设置窗口过期时间，避免计数永久累积
        if (current != null && current == 1L) {
            redisTemplate.expire(redisKey, rateLimit.seconds(), TimeUnit.SECONDS);
        }

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
