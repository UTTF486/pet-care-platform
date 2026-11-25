package com.petcare.task;

import com.petcare.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单超时自动取消任务。
 * <p>
 * 实现方式：每分钟扫一次 t_order 里 status=0 且 expire_time <= now 的订单。
 * 当前采用定时扫表配合 idx_status_expire 索引，避免引入额外调度基础设施。
 * <p>
 * 后续演进方向：
 * 1) 订单表大了之后，调度任务拆到独立服务，避免多实例重复执行（可用 Redis 锁 / XXL-JOB）；
 * 2) 改用延迟消息，到期精确取消，不用轮询；
 * 3) 加监控告警：单次取消量突增说明商家侧出问题了。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    /** 每次最多处理 50 单，避免单次任务占用连接太久 */
    private static final int BATCH_SIZE = 50;

    private final OrderService orderService;

    /**
     * 每分钟第 0 秒执行。
     * cron 6 位：秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 * * * * ?")
    public void cancelTimeoutOrders() {
        try {
            int count = orderService.cancelTimeoutOrders(BATCH_SIZE);
            if (count > 0) {
                log.info("[订单超时任务] 本轮自动取消 {} 笔超时未接单订单", count);
            }
        } catch (Exception e) {
            // 定时任务里必须自己兜住异常，否则一次失败可能导致后续调度中断（取决于调度器实现）
            log.error("[订单超时任务] 执行异常", e);
        }
    }
}
