package com.petcare.common;

/**
 * 订单状态常量 + 文案。
 * 不用枚举是因为 MySQL 里存的是 tinyint，用常量更直接；
 * 状态流转规则在 OrderService 里集中判断，避免散落各处。
 */
public final class OrderStatus {

    /** 待接单：用户已支付/下单，等商家接单 */
    public static final int PENDING_ACCEPT = 0;
    /** 待服务：商家已接单并指派服务师 */
    public static final int PENDING_SERVICE = 1;
    /** 已完成：服务结束 */
    public static final int FINISHED = 2;
    /** 已取消：用户主动取消 / 超时自动取消 */
    public static final int CANCELED = 3;

    private OrderStatus() {
    }

    public static String text(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case PENDING_ACCEPT:
                return "待接单";
            case PENDING_SERVICE:
                return "待服务";
            case FINISHED:
                return "已完成";
            case CANCELED:
                return "已取消";
            default:
                return "未知";
        }
    }
}
