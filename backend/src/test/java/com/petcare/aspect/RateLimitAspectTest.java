package com.petcare.aspect;

import com.petcare.annotation.RateLimit;
import com.petcare.common.BusinessException;
import com.petcare.common.UserContext;
import com.petcare.config.ClientIpResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitAspectTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
    private final RateLimitAspect aspect = new RateLimitAspect(redisTemplate, clientIpResolver);

    @BeforeEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void publicEndpointFailsOpenWhenRedisIsUnavailable() throws Throwable {
        RateLimit rateLimit = rateLimit(true);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(redisTemplate.execute(any(), anyList(), any())).thenThrow(new RuntimeException("redis down"));
        when(joinPoint.proceed()).thenReturn("ok");

        assertEquals("ok", aspect.around(joinPoint, rateLimit));
    }

    @Test
    void authEndpointFailsClosedWhenRedisIsUnavailable() {
        RateLimit rateLimit = rateLimit(false);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(redisTemplate.execute(any(), anyList(), any())).thenThrow(new RuntimeException("redis down"));

        assertThrows(BusinessException.class, () -> aspect.around(joinPoint, rateLimit));
    }

    private RateLimit rateLimit(boolean failOpen) {
        RateLimit rateLimit = mock(RateLimit.class);
        when(rateLimit.key()).thenReturn("test");
        when(rateLimit.count()).thenReturn(10);
        when(rateLimit.seconds()).thenReturn(60);
        when(rateLimit.message()).thenReturn("limited");
        when(rateLimit.failOpen()).thenReturn(failOpen);
        return rateLimit;
    }
}
