package com.petcare.aspect;

import com.petcare.annotation.RateLimit;
import com.petcare.common.BusinessException;
import com.petcare.common.ResultCode;
import com.petcare.common.UserContext;
import com.petcare.config.ClientIpResolver;
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

import jakarta.servlet.http.HttpServletRequest;
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
    private final ClientIpResolver clientIpResolver;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String identity = resolveIdentity();
        String redisKey = KEY_PREFIX + rateLimit.key() + ":" + identity;

        Long current;
        try {
            current = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    Collections.singletonList(redisKey),
                    String.valueOf(rateLimit.seconds()));
        } catch (Exception e) {
            log.error("限流 Redis 异常, action={}, failOpen={}", rateLimit.key(), rateLimit.failOpen(), e);
            if (rateLimit.failOpen()) {
                // 公共浏览接口不能因为限流组件故障整体不可用。
                return joinPoint.proceed();
            }
            throw new BusinessException(ResultCode.ERROR, "安全校验服务暂时不可用，请稍后再试");
        }

        if (current != null && current > rateLimit.count()) {
            log.warn("触发限流, action={}, 当前次数={}", rateLimit.key(), current);
            throw new BusinessException(ResultCode.RATE_LIMIT, rateLimit.message());
        }

        return joinPoint.proceed();
    }

    /**
     * 优先按用户维度限流；未登录或公开接口按可信代理解析后的客户端 IP。
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
            return "ip" + clientIpResolver.resolve(request);
        }
        return "anonymous";
    }
}
