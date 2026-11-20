package com.petcare.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 服务项目排行榜（营收/单量维度），看板右侧柱状图用。
 */
@Data
public class ServiceRankVO {

    private Long serviceId;
    private String serviceName;
    private Long orderCount;
    private BigDecimal revenue;
}
