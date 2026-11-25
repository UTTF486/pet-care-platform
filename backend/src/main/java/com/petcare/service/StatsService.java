package com.petcare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.common.BusinessException;
import com.petcare.common.ResultCode;
import com.petcare.mapper.OrderMapper;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_RANGE = 7;
    private static final int MAX_RANGE = 365;

    private final OrderMapper orderMapper;
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

        Map<String, Object> overview = orderMapper.selectOverview(
                LocalDate.now(BUSINESS_ZONE).atStartOfDay());
        StatsOverviewVO vo = new StatsOverviewVO();
        vo.setTodayOrderCount(toLong(overview.get("todayOrderCount")));
        vo.setTodayRevenue(toDecimal(overview.get("todayRevenue")));
        vo.setTotalOrderCount(toLong(overview.get("totalOrderCount")));
        vo.setTotalRevenue(toDecimal(overview.get("totalRevenue")));
        vo.setPendingCount(toLong(overview.get("pendingCount")));
        vo.setUserCount(toLong(overview.get("userCount")));
        vo.setOnSaleServiceCount(toLong(overview.get("onSaleServiceCount")));

        long totalReview = toLong(overview.get("totalReview"));
        if (totalReview > 0) {
            long good = toLong(overview.get("goodReview"));
            vo.setGoodRate(BigDecimal.valueOf(good * 100.0 / totalReview).setScale(1, RoundingMode.HALF_UP));
        } else {
            vo.setGoodRate(BigDecimal.ZERO);
        }
        vo.setAvgRating(toDecimal(overview.get("avgRating")));

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
        String safeType = type == null ? "day" : type;
        int safeRange = normalizeRange(range);
        List<TrendPointVO> actual = orderMapper.selectTrend(
                resolveDateFormat(safeType), resolveStart(safeType, safeRange));
        Map<String, TrendPointVO> byLabel = new LinkedHashMap<>();
        for (TrendPointVO point : actual) {
            byLabel.put(point.getLabel(), point);
        }

        List<TrendPointVO> result = new ArrayList<>();
        for (String label : buildTrendLabels(safeType, safeRange, LocalDate.now(BUSINESS_ZONE))) {
            TrendPointVO point = byLabel.get(label);
            if (point == null) {
                point = new TrendPointVO();
                point.setLabel(label);
                point.setOrderCount(0L);
                point.setRevenue(BigDecimal.ZERO);
            }
            result.add(point);
        }
        return result;
    }

    /**
     * 服务项目排行，看板柱状图。
     */
    public List<ServiceRankVO> ranking(String type, int range, int limit) {
        String safeType = type == null ? "day" : type;
        // 复用趋势统计的类型校验，避免非法参数被静默当成 day。
        resolveDateFormat(safeType);
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        return orderMapper.selectServiceRank(resolveStart(safeType, range), safeLimit);
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
        int safeRange = normalizeRange(range);
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
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

    private int normalizeRange(int range) {
        return range <= 0 ? DEFAULT_RANGE : Math.min(range, MAX_RANGE);
    }

    static List<String> buildTrendLabels(String type, int range, LocalDate today) {
        int safeRange = range <= 0 ? DEFAULT_RANGE : Math.min(range, MAX_RANGE);
        List<String> labels = new ArrayList<>();
        switch (type == null ? "day" : type) {
            case "day":
                for (int i = safeRange - 1; i >= 0; i--) {
                    labels.add(today.minusDays(i).toString());
                }
                return labels;
            case "week":
                WeekFields iso = WeekFields.ISO;
                LocalDate weekStart = today.minusWeeks(safeRange - 1L).with(DayOfWeek.MONDAY);
                for (int i = 0; i < safeRange; i++) {
                    LocalDate week = weekStart.plusWeeks(i);
                    labels.add(String.format(Locale.ROOT, "%04d-%02d",
                            week.get(iso.weekBasedYear()), week.get(iso.weekOfWeekBasedYear())));
                }
                return labels;
            case "month":
                YearMonth current = YearMonth.from(today);
                for (int i = safeRange - 1; i >= 0; i--) {
                    labels.add(current.minusMonths(i).toString());
                }
                return labels;
            default:
                throw new BusinessException(ResultCode.PARAM_ERROR, "type 只支持 day/week/month");
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
