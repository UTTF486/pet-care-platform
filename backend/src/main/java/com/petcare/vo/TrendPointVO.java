package com.petcare.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 报表折线图的一个点：日/周/月维度下的订单量与营收。
 */
@Data
public class TrendPointVO {

    /** x 轴标签，如 2026-11-20 / 2026-46周 / 2026-11 */
    private String label;
    /** 订单量 */
    private Long orderCount;
    /** 营收 */
    private BigDecimal revenue;
}
