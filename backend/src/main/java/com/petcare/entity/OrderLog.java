package com.petcare.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态流转日志，对应 t_order_log。
 * 每一次状态变更都插一条，售后纠纷时可以完整还原"谁在什么时候改了什么"。
 */
@Data
public class OrderLog {

    private Long id;
    private Long orderId;
    /** 变更前状态 */
    private Integer fromStatus;
    /** 变更后状态 */
    private Integer toStatus;
    /** STATUS_CHANGE / ORDER_CREATED / URGED / REVIEWED / TIMEOUT_CANCEL */
    private String eventType;
    /** 0-用户 1-商家 2-系统 */
    private Integer operatorType;
    private Long operatorId;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
