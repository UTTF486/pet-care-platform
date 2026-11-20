package com.petcare.mapper;

import com.petcare.entity.OrderLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单流转日志 Mapper。
 */
public interface OrderLogMapper {

    int insert(OrderLog log);

    List<OrderLog> selectByOrderId(@Param("orderId") Long orderId);
}
