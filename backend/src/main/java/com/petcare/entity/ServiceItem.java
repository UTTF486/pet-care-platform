package com.petcare.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 服务项目，对应 t_service。
 */
@Data
public class ServiceItem {

    public interface Create {
    }

    public interface Update {
    }

    private Long id;

    @NotNull(message = "请选择服务品类", groups = Create.class)
    private Long categoryId;

    @NotBlank(message = "服务名称不能为空", groups = Create.class)
    @Size(min = 1, max = 64, message = "服务名称需为 1-64 个字符",
            groups = {Create.class, Update.class})
    private String name;

    @Size(max = 255, message = "封面地址最多 255 个字符", groups = {Create.class, Update.class})
    private String cover;

    @Size(max = 500, message = "服务说明最多 500 个字符", groups = {Create.class, Update.class})
    private String description;

    @NotNull(message = "价格不能为空", groups = Create.class)
    @DecimalMin(value = "0.01", message = "价格必须大于 0", groups = {Create.class, Update.class})
    @DecimalMax(value = "99999999.99", message = "价格超出允许范围",
            groups = {Create.class, Update.class})
    @Digits(integer = 8, fraction = 2, message = "价格最多保留两位小数",
            groups = {Create.class, Update.class})
    private BigDecimal price;

    /** 服务时长，单位分钟 */
    @Min(value = 1, message = "服务时长必须大于 0", groups = {Create.class, Update.class})
    @Max(value = 1440, message = "服务时长不能超过 1440 分钟", groups = {Create.class, Update.class})
    private Integer duration;

    /** 1-上架 0-下架 */
    @Min(value = 0, message = "服务状态只能是 0 或 1", groups = {Create.class, Update.class})
    @Max(value = 1, message = "服务状态只能是 0 或 1", groups = {Create.class, Update.class})
    private Integer status;

    /** 累计销量，冗余字段，下单完成后 +1 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer sales;

    /**
     * 关联查询带出的品类名（非表字段）。
     * MyBatis 查询时用 c.name AS category_name 自动映射进来。
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String categoryName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime updateTime;
}
