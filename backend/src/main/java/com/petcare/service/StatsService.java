package com.petcare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.common.BusinessException;
import com.petcare.common.OrderStatus;
import com.petcare.common.ResultCode;
import com.petcare.mapper.OrderMapper;
import com.petcare.mapper.ReviewMapper;
import com.petcare.mapper.ServiceItemMapper;
import com.petcare.mapper.UserMapper;
import com.petcare.vo.ServiceRankVO;
import com.petcare.vo.StatsOverviewVO;
import com.petcare.vo.TrendPointVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 商家数据统计服务。
 * <p>
 * 设计要点：
 * 1. 报表聚合交给 MySQL 做（GROUP BY + DATE_FORMAT），不要捞到内存里算 —— 数据量大时会 OOM；
 * 2. 营收只统计"已完成"订单，待服务和已取消的钱不算收入；
 * 3. 看板接口加了 60 秒缓存，因为它是后台首页，刷新频率高但数据实时性要求没那么强。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final String KEY_OVERVIEW = "cache:stats:overview";

    private final OrderMapper orderMapper;
    private final ReviewMapper reviewMapper;
    private final UserMapper userMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${pet.cache.stats-seconds:60}")
    private long statsSeconds;

    /**
     * 看板顶部指标。
     */
    public StatsOverviewVO overview() {
        // 先看缓存
        try {
            String cached = redisTemplate.opsForValue().get(KEY_OVERVIEW);
            if (cached != null) {
                return objectMapper.readValue(cached, StatsOverviewVO.class);
            }
        } catch (Exception e) {
            log.warn("读取看板缓存失败，降级查库", e);
        }

        StatsOverviewVO vo = new StatsOverviewVO();

        // 今日 / 累计：同一个 SQL，传不传 start 决定范围
        Map<String, Object> today = orderMapper.selectStatRange(LocalDate.now().atStartOfDay());
        Map<String, Object> all = orderMapper.selectStatRange(null);
        vo.setTodayOrderCount(toLong(today.get("orderCount")));
        vo.setTodayRevenue(toDecimal(today.get("revenue")));
        vo.setTotalOrderCount(toLong(all.get("orderCount")));
        vo.setTotalRevenue(toDecimal(all.get("revenue")));

        vo.setPendingCount(orderMapper.countByStatus(OrderStatus.PENDING_ACCEPT));
        vo.setUserCount(userMapper.countAll());
        vo.setOnSaleServiceCount(serviceItemMapper.countOnSale());

        // 好评率 = 4 星及以上 / 总评价数
        long totalReview = reviewMapper.countAll();
        if (totalReview > 0) {
            long good = reviewMapper.countGood();
            vo.setGoodRate(BigDecimal.valueOf(good * 100.0 / totalReview).setScale(1, RoundingMode.HALF_UP));
        } else {
            vo.setGoodRate(BigDecimal.ZERO);
        }
        vo.setAvgRating(reviewMapper.avgRating());

        try {
            redisTemplate.opsForValue().set(KEY_OVERVIEW,
                    objectMapper.writeValueAsString(vo), statsSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写看板缓存失败", e);
        }
        return vo;
    }

    /**
     * 趋势报表。
     *
     * @param type  day / week / month
     * @param range 统计多少个周期，如 day+7 = 最近 7 天
     */
    public List<TrendPointVO> trend(String type, int range) {
        return orderMapper.selectTrend(resolveDateFormat(type), resolveStart(type, range));
    }

    /**
     * 服务项目排行，看板柱状图。
     */
    public List<ServiceRankVO> ranking(String type, int range, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        return orderMapper.selectServiceRank(resolveStart(type, range), safeLimit);
    }

    // ---------------- 私有方法 ----------------

    private String resolveDateFormat(String type) {
        switch (type == null ? "day" : type) {
            case "day":
                return "%Y-%m-%d";
            case "week":
                return "%x-%v";     // 年-第几周
            case "month":
                return "%Y-%m";
            default:
                throw new BusinessException(ResultCode.PARAM_ERROR, "type 只支持 day/week/month");
        }
    }

    /**
     * 计算统计起点：
     * day   -> 最近 range 天的 00:00
     * week  -> 最近 range 周，从周一开始
     * month -> 最近 range 个月，从 1 号开始
     */
    private LocalDateTime resolveStart(String type, int range) {
        int safeRange = range <= 0 ? 7 : range;
        LocalDate today = LocalDate.now();
        switch (type == null ? "day" : type) {
            case "day":
                return today.minusDays(safeRange - 1L).atStartOfDay();
            case "week":
                return today.minusWeeks(safeRange - 1L).with(DayOfWeek.MONDAY).atStartOfDay();
            case "month":
                return today.withDayOfMonth(1).minusMonths(safeRange - 1L).atStartOfDay();
            default:
                return today.minusDays(6).atStartOfDay();
        }
    }

    /** MySQL COUNT 返回 Long，SUM 返回 BigDecimal，统一转一下避免类型强转异常 */
    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(value.toString());
    }
}
