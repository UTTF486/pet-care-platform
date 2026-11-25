package com.petcare.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 服务师，对应 t_servicer。
 */
@Data
public class Servicer {

    public interface Create {
    }

    public interface Update {
    }

    private Long id;

    @NotBlank(message = "服务师姓名不能为空", groups = Create.class)
    @Size(min = 1, max = 32, message = "服务师姓名需为 1-32 个字符", groups = {Create.class, Update.class})
    private String name;

    @Size(max = 20, message = "联系电话最多 20 个字符", groups = {Create.class, Update.class})
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确", groups = {Create.class, Update.class})
    private String phone;

    @Size(max = 255, message = "头像地址最多 255 个字符", groups = {Create.class, Update.class})
    private String avatar;

    /** 擅长技能，逗号分隔，如 "猫咪喂养,喂药" */
    @Size(max = 255, message = "服务技能最多 255 个字符", groups = {Create.class, Update.class})
    private String skills;

    /** 1-初级 2-中级 3-高级 */
    @Min(value = 1, message = "服务师等级只能是 1、2 或 3", groups = {Create.class, Update.class})
    @Max(value = 3, message = "服务师等级只能是 1、2 或 3", groups = {Create.class, Update.class})
    private Integer level;

    /** 评分 0-5，由评价表聚合更新 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal rating;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer orderCount;

    /** 1-可接单 0-休息 */
    @Min(value = 0, message = "服务师状态只能是 0 或 1", groups = {Create.class, Update.class})
    @Max(value = 1, message = "服务师状态只能是 0 或 1", groups = {Create.class, Update.class})
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createTime;
}
