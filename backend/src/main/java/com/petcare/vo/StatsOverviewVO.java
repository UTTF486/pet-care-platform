package com.petcare.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 数据看板顶部指标卡。
 */
@Data
public class StatsOverviewVO {

    /** 今日订单量 */
    private Long todayOrderCount;
    /** 今日营收（仅统计已完成订单） */
    private BigDecimal todayRevenue;
    /** 累计订单量 */
    private Long totalOrderCount;
    /** 累计营收 */
    private BigDecimal totalRevenue;
    /** 当前待接单数量，运营最关心的数字 */
    private Long pendingCount;
    /** 累计用户数 */
    private Long userCount;
    /** 好评率(%)，rating>=4 记为好评 */
    private BigDecimal goodRate;
    /** 平均评分 */
    private BigDecimal avgRating;
    /** 上架服务数 */
    private Long onSaleServiceCount;
}
