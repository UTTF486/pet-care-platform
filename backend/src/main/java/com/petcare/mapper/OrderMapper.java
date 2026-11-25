package com.petcare.mapper;

import com.petcare.entity.Order;
import com.petcare.vo.OrderVO;
import com.petcare.vo.ServiceRankVO;
import com.petcare.vo.TrendPointVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单 Mapper（核心）。
 * 所有"状态变更"的 SQL 都带 WHERE status = 期望值，
 * 靠数据库行锁保证并发下状态不会被改乱（相当于乐观锁）。
 */
public interface OrderMapper {

    int insert(Order order);

    Order selectByUserAndRequestId(@Param("userId") Long userId,
                                   @Param("requestId") String requestId);

    /** 捕获唯一键冲突后使用当前读，避免 REPEATABLE READ 旧快照看不到并发提交。 */
    Order selectByUserAndRequestIdForUpdate(@Param("userId") Long userId,
                                            @Param("requestId") String requestId);

    OrderVO selectDetail(@Param("id") Long id);

    /** 状态流转只需订单主表字段，避免每次操作都执行五表 JOIN 和评价子查询。 */
    Order selectState(@Param("id") Long id);

    List<OrderVO> selectUserPage(@Param("userId") Long userId,
                                 @Param("status") Integer status,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    long countUser(@Param("userId") Long userId, @Param("status") Integer status);

    List<OrderVO> selectAdminPage(@Param("status") Integer status,
                                  @Param("keyword") String keyword,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    long countAdmin(@Param("status") Integer status, @Param("keyword") String keyword);

    /** 接单：0 -> 1，同时指派服务师 */
    int accept(@Param("id") Long id, @Param("servicerId") Long servicerId);

    /** 完成：1 -> 2 */
    int finish(@Param("id") Long id);

    /** 取消：0/1 -> 3（用户主动取消或商家拒单） */
    int cancel(@Param("id") Long id, @Param("reason") String reason);

    /** 超时取消：只允许 0 -> 3，避免把已经接单的订单误取消 */
    int cancelTimeout(@Param("id") Long id, @Param("reason") String reason);

    /** 同一服务师已有待服务订单与预约区间重叠时返回冲突订单 id。 */
    List<Long> selectServicerScheduleConflicts(@Param("servicerId") Long servicerId,
                                               @Param("appointmentStart") LocalDateTime appointmentStart,
                                               @Param("appointmentEnd") LocalDateTime appointmentEnd,
                                               @Param("excludeOrderId") Long excludeOrderId);

    /** 删除宠物前判断是否已有历史订单 */
    long countByPetId(@Param("petId") Long petId);

    /** 定时任务：捞出已经超时的待接单订单 */
    List<Order> selectTimeoutList(@Param("limit") int limit);

    /** 看板指标一次查询完成，减少后台首页的数据库往返。 */
    Map<String, Object> selectOverview(@Param("todayStart") LocalDateTime todayStart);

    /**
     * 报表趋势：按传入的日期格式聚合。
     *
     * @param dateFormat MySQL 日期格式，如 %Y-%m-%d(日) %x-%v(周) %Y-%m(月)
     */
    List<TrendPointVO> selectTrend(@Param("dateFormat") String dateFormat,
                                   @Param("start") LocalDateTime start);

    /** 服务项目排行：按单量倒序 */
    List<ServiceRankVO> selectServiceRank(@Param("start") LocalDateTime start,
                                          @Param("limit") int limit);
}
