package com.petcare.vo;

import com.petcare.common.OrderStatus;
import com.petcare.entity.Order;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单展示对象：订单表 LEFT JOIN 宠物表、服务表、服务师表后多出来的字段。
 * 继承 Order 复用 20 多个字段，只补展示需要的。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderVO extends Order {

    /** 宠物名，列表页展示"给谁服务" */
    private String petName;
    private String petType;

    /** 服务封面，订单详情页用；时长使用订单表中的快照字段。 */
    private String serviceCover;

    /** 接单服务师姓名 */
    private String servicerName;

    /** 下单用户昵称，后台订单列表用 */
    private String userNickname;

    /** 是否已评价，控制"去评价"按钮 */
    private Boolean reviewed;

    /**
     * 状态中文，前端直接展示。
     * 这个方法没有对应字段，Jackson 会序列化成 statusText 属性。
     */
    public String getStatusText() {
        return OrderStatus.text(getStatus());
    }
}
