package com.petcare.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 宠物档案，对应 t_pet。
 * 校验注解直接写在实体上，Controller 用 @Valid 触发，少写一层 DTO。
 */
@Data
public class Pet {

    public interface Create {
    }

    public interface Update {
    }

    private Long id;

    /** 归属用户，由后端从上下文取，前端不允许传 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long userId;

    @NotBlank(message = "宠物名不能为空", groups = Create.class)
    @Size(min = 1, max = 32, message = "宠物名需为 1-32 个字符", groups = {Create.class, Update.class})
    private String name;

    /** 猫/狗/其他 */
    @Pattern(regexp = "猫|狗|其他", message = "宠物类型只能是猫、狗或其他",
            groups = {Create.class, Update.class})
    private String petType;

    @Size(max = 32, message = "品种最多 32 个字符", groups = {Create.class, Update.class})
    private String breed;

    /** 0-未知 1-公 2-母 */
    @Min(value = 0, message = "宠物性别不合法", groups = {Create.class, Update.class})
    @Max(value = 2, message = "宠物性别不合法", groups = {Create.class, Update.class})
    private Integer gender;

    /** 年龄，单位月 */
    @Min(value = 0, message = "宠物年龄不能为负数", groups = {Create.class, Update.class})
    @Max(value = 600, message = "宠物年龄不能超过 600 个月", groups = {Create.class, Update.class})
    private Integer age;

    /** 体重 kg */
    @DecimalMin(value = "0.01", message = "宠物体重必须大于 0", groups = {Create.class, Update.class})
    @DecimalMax(value = "999.99", message = "宠物体重最多 999.99 kg", groups = {Create.class, Update.class})
    @Digits(integer = 3, fraction = 2, message = "宠物体重最多保留两位小数",
            groups = {Create.class, Update.class})
    private BigDecimal weight;

    @Size(max = 255, message = "头像地址最多 255 个字符", groups = {Create.class, Update.class})
    private String avatar;

    /** 备注：怕生、需喂药等，服务师必看 */
    @Size(max = 255, message = "备注最多 255 个字符", groups = {Create.class, Update.class})
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime updateTime;
}
