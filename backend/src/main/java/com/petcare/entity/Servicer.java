package com.petcare.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 服务师，对应 t_servicer。
 */
@Data
public class Servicer {

    private Long id;

    @NotBlank(message = "服务师姓名不能为空")
    private String name;

    private String phone;
    private String avatar;

    /** 擅长技能，逗号分隔，如 "猫咪喂养,喂药" */
    private String skills;

    /** 1-初级 2-中级 3-高级 */
    private Integer level;

    /** 评分 0-5，由评价表聚合更新 */
    private BigDecimal rating;

    private Integer orderCount;

    /** 1-可接单 0-休息 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
