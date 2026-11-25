package com.petcare.task;

import com.petcare.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 订单超时自动取消任务。
 * <p>
 * 实现方式：每分钟扫一次 t_order 里 status=0 且 expire_time <= now 的订单。
 * 当前采用定时扫表配合 idx_status_expire 索引，避免引入额外调度基础设施。
 * <p>
 * 通过 Redis SET NX 锁保证同一时刻只有一个实例扫描；订单本身的取消仍由
 * status=0 条件更新兜底。后续订单量变大可迁移到 XXL-JOB 或延迟消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    /** 每次最多处理 50 单，避免单次任务占用连接太久 */
    private static final int BATCH_SIZE = 50;
    private static final String LOCK_KEY = "lock:order:timeout";
    private static final RedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]); else return 0; end;",
            Long.class);

    private final OrderService orderService;
    private final StringRedisTemplate redisTemplate;

    @Value("${pet.order.timeout-lock-seconds:300}")
    private long lockSeconds;

    /**
     * 每分钟第 0 秒执行。
     * cron 6 位：秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 * * * * ?")
    public void cancelTimeoutOrders() {
        String lockValue = UUID.randomUUID().toString();
        boolean lockHeld = false;
        try {
            try {
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                        LOCK_KEY, lockValue, lockSeconds, TimeUnit.SECONDS);
                lockHeld = Boolean.TRUE.equals(acquired);
                if (!lockHeld) {
                    log.debug("[订单超时任务] 其他实例正在执行，跳过本轮");
                    return;
                }
            } catch (Exception e) {
                // Redis 故障时降级执行；取消 SQL 的 status=0 条件更新仍能防止重复处理。
                log.error("[订单超时任务] Redis 锁不可用，降级执行单实例保护关闭", e);
            }

            int count = orderService.cancelTimeoutOrders(BATCH_SIZE);
            if (count > 0) {
                log.info("[订单超时任务] 本轮自动取消 {} 笔超时未接单订单", count);
            }
        } catch (Exception e) {
            // 定时任务里必须自己兜住异常，否则一次失败可能导致后续调度中断（取决于调度器实现）
            log.error("[订单超时任务] 执行异常", e);
        } finally {
            if (lockHeld) {
                try {
                    redisTemplate.execute(
                            RELEASE_LOCK_SCRIPT,
                            Collections.singletonList(LOCK_KEY),
                            lockValue);
                } catch (Exception e) {
                    log.warn("[订单超时任务] 释放 Redis 锁失败", e);
                }
            }
        }
    }
}
