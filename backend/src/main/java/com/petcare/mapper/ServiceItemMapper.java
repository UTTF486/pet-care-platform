package com.petcare.mapper;

import com.petcare.entity.ServiceItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 服务项目 Mapper。
 */
public interface ServiceItemMapper {

    ServiceItem selectById(@Param("id") Long id);

    /**
     * 通用列表查询。
     *
     * @param categoryId 品类，null 表示全部
     * @param keyword    名称模糊搜索，null 表示不过滤
     * @param status     1-上架 0-下架 null-不限（后台要查全部）
     */
    List<ServiceItem> selectList(@Param("categoryId") Long categoryId,
                                 @Param("keyword") String keyword,
                                 @Param("status") Integer status,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    long countList(@Param("categoryId") Long categoryId,
                   @Param("keyword") String keyword,
                   @Param("status") Integer status);

    /** 热门服务：按销量倒序，用户端首页用，会被 Redis 缓存 */
    List<ServiceItem> selectHot(@Param("limit") int limit);

    int insert(ServiceItem item);

    int update(ServiceItem item);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 订单完成后累加销量 */
    int increaseSales(@Param("id") Long id);

}
