package com.petcare.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务品类，对应 t_category。
 */
@Data
public class Category {

    private Long id;
    private String name;
    private String icon;
    private Integer sort;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
