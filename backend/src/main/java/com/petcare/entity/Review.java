package com.petcare.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务评价，对应 t_review。一单一评，由数据库唯一索引兜底防重复。
 */
@Data
public class Review {

    private Long id;
    private Long orderId;
    private Long userId;
    private Long serviceId;
    private Long servicerId;
    /** 1-5 星，>=4 记为好评 */
    private Integer rating;
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
