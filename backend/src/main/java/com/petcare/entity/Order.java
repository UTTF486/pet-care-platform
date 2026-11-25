package com.petcare.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单实体，对应 t_order。
 * <p>
 * serviceName / servicePrice 是下单时的快照：
 * 商家后续改价、改名都不能影响历史订单，这在真实交易系统里是硬要求。
 */
@Data
public class Order {

    private Long id;
    private String orderNo;
    /** 客户端幂等请求号，和 user_id 组成唯一键。 */
    private String requestId;
    /** requestId 对应的下单参数指纹，用于拒绝同 requestId 不同 payload。 */
    private String requestHash;
    private Long userId;
    private Long petId;
    private Long serviceId;
    private Long servicerId;

    private String serviceName;
    private BigDecimal servicePrice;
    private BigDecimal amount;
    /** 下单时的服务时长快照，避免后续服务改时长影响历史订单。 */
    private Integer serviceDuration;

    private String contactName;
    private String contactPhone;
    private String address;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate appointDate;

    private String timeSlot;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime appointmentStart;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime appointmentEnd;

    /** 0-待接单 1-待服务 2-已完成 3-已取消，取值见 OrderStatus */
    private Integer status;

    private String remark;
    private String cancelReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 超时未接单的自动取消时间点 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime acceptTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelTime;
}
