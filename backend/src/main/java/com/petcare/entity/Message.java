package com.petcare.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知，对应 t_message。
 * user_id = 0 表示发给商家后台（催单就是这条）。
 */
@Data
public class Message {

    /** 商家后台信箱的约定 id */
    public static final long ADMIN_USER_ID = 0L;

    private Long id;
    private Long userId;
    private Long orderId;
    /** ORDER-订单通知 URGE-催单 SYSTEM-系统 */
    private String type;
    private String title;
    private String content;
    private Integer isRead;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
