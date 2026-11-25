package com.petcare.mapper;

import com.petcare.entity.Servicer;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 服务师 Mapper。
 */
public interface ServicerMapper {

    Servicer selectById(@Param("id") Long id);

    /** 接单时选人：只查"可接单"状态的 */
    List<Servicer> selectAvailable();

    /** 接单事务先锁定服务师行，串行化同一服务师的并发接单检查 */
    Servicer lockAvailableById(@Param("id") Long id);

    List<Servicer> selectPage(@Param("keyword") String keyword,
                              @Param("offset") int offset,
                              @Param("size") int size);

    long countPage(@Param("keyword") String keyword);

    int insert(Servicer servicer);

    int update(Servicer servicer);

    /** 接单后累计接单数 +1 */
    int increaseOrderCount(@Param("id") Long id);

    /** 根据 t_review 聚合结果刷新服务师评分 */
    int refreshRatingFromReviews(@Param("id") Long id);
}
