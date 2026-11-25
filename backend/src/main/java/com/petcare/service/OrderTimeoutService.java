package com.petcare.service;

import com.petcare.common.OrderStatus;
import com.petcare.entity.Message;
import com.petcare.entity.Order;
import com.petcare.entity.OrderLog;
import com.petcare.mapper.OrderLogMapper;
import com.petcare.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 超时订单单笔事务边界。
 * 独立 Bean 是必要的：同类方法自调用不会经过 Spring 事务代理。
 */
@Service
@RequiredArgsConstructor
public class OrderTimeoutService {

    private final OrderMapper orderMapper;
    private final OrderLogMapper orderLogMapper;
    private final MessageService messageService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean cancelOne(Order order) {
        String reason = "超时未接单，系统自动取消";
        // status=0 条件更新与接单并发时只有一个请求能成功。
        int rows = orderMapper.cancelTimeout(order.getId(), reason);
        if (rows == 0) {
            return false;
        }

        OrderLog log = new OrderLog();
        log.setOrderId(order.getId());
        log.setFromStatus(OrderStatus.PENDING_ACCEPT);
        log.setToStatus(OrderStatus.CANCELED);
        log.setEventType("TIMEOUT_CANCEL");
        log.setOperatorType(2);
        log.setOperatorId(0L);
        log.setRemark("系统超时自动取消");
        orderLogMapper.insert(log);

        messageService.send(order.getUserId(), order.getId(), "ORDER", "订单已自动取消",
                "订单 " + order.getOrderNo() + " 超时未接单，已自动取消，可重新预约");
        messageService.send(Message.ADMIN_USER_ID, order.getId(), "ORDER", "订单超时自动取消",
                "订单 " + order.getOrderNo() + " 超时未接单，系统已自动取消");
        return true;
    }
}
