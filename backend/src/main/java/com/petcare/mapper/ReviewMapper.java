package com.petcare.mapper;

import com.petcare.entity.Review;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评价 Mapper。
 */
public interface ReviewMapper {

    int insert(Review review);

    Review selectByOrderId(@Param("orderId") Long orderId);

    List<Review> selectByServiceId(@Param("serviceId") Long serviceId);
}
