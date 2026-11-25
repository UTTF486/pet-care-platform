package com.petcare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.common.BusinessException;
import com.petcare.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class StatsServiceTest {

    @Test
    void fillsMissingDays() {
        List<String> labels = StatsService.buildTrendLabels(
                "day", 3, LocalDate.of(2026, 11, 25));
        assertEquals(Arrays.asList("2026-11-23", "2026-11-24", "2026-11-25"), labels);
    }

    @Test
    void fillsMonthLabels() {
        List<String> labels = StatsService.buildTrendLabels(
                "month", 3, LocalDate.of(2026, 11, 25));
        assertEquals(Arrays.asList("2026-09", "2026-10", "2026-11"), labels);
    }

    @Test
    void fillsWeekLabels() {
        List<String> labels = StatsService.buildTrendLabels(
                "week", 2, LocalDate.of(2026, 11, 25));
        assertEquals(2, labels.size());
        assertEquals("2026-47", labels.get(0));
        assertEquals("2026-48", labels.get(1));
    }

    @Test
    void rankingRejectsUnknownType() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        StatsService service = new StatsService(
                orderMapper, mock(StringRedisTemplate.class), new ObjectMapper());

        assertThrows(BusinessException.class, () -> service.ranking("invalid", 7, 5));
        verifyNoInteractions(orderMapper);
    }
}
