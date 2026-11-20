package com.petcare.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 提交评价参数。
 */
@Data
public class ReviewDTO {

    @NotNull(message = "订单 id 不能为空")
    private Long orderId;

    @NotNull(message = "请打分")
    @Min(value = 1, message = "评分最低 1 星")
    @Max(value = 5, message = "评分最高 5 星")
    private Integer rating;

    @Size(max = 500, message = "评价内容最多 500 字")
    private String content;
}
