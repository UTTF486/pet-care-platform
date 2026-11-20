package com.petcare.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 服务项目，对应 t_service。
 */
@Data
public class ServiceItem {

    private Long id;

    @NotNull(message = "请选择服务品类")
    private Long categoryId;

    @NotBlank(message = "服务名称不能为空")
    private String name;

    private String cover;

    private String description;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;

    /** 服务时长，单位分钟 */
    private Integer duration;

    /** 1-上架 0-下架 */
    private Integer status;

    /** 累计销量，冗余字段，下单完成后 +1 */
    private Integer sales;

    /**
     * 关联查询带出的品类名（非表字段）。
     * MyBatis 查询时用 c.name AS category_name 自动映射进来。
     */
    private String categoryName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
