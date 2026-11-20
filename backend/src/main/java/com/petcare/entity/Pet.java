package com.petcare.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 宠物档案，对应 t_pet。
 * 校验注解直接写在实体上，Controller 用 @Valid 触发，少写一层 DTO。
 */
@Data
public class Pet {

    private Long id;

    /** 归属用户，由后端从上下文取，前端不允许传 */
    private Long userId;

    @NotBlank(message = "宠物名不能为空")
    private String name;

    /** 猫/狗/其他 */
    private String petType;

    private String breed;

    /** 0-未知 1-公 2-母 */
    private Integer gender;

    /** 年龄，单位月 */
    private Integer age;

    /** 体重 kg */
    private BigDecimal weight;

    private String avatar;

    /** 备注：怕生、需喂药等，服务师必看 */
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
