package com.petcare.util;

import com.petcare.common.BusinessException;
import com.petcare.common.ResultCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 预约时段白名单与“开始时间是否已过”的统一判断。
 * 日期和时段必须组合成完整时间后再与当前时间比较，不能只看日期。
 */
public final class AppointmentTimeUtil {

    private static final List<String> TIME_SLOTS = Collections.unmodifiableList(Arrays.asList(
            "08:00-10:00", "10:00-12:00", "14:00-16:00", "16:00-18:00", "18:00-20:00"));

    private AppointmentTimeUtil() {
    }

    public static List<String> timeSlots() {
        return TIME_SLOTS;
    }

    public static boolean isValidSlot(String timeSlot) {
        return timeSlot != null && TIME_SLOTS.contains(timeSlot);
    }

    /**
     * 返回预约时段在当天的开始时间。
     */
    public static LocalDateTime startAt(LocalDate appointDate, String timeSlot) {
        if (appointDate == null || !isValidSlot(timeSlot)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "预约日期或时段不合法");
        }
        return LocalDateTime.of(appointDate, LocalTime.parse(timeSlot.substring(0, 5)));
    }

    public static LocalDateTime endAt(LocalDate appointDate, String timeSlot, int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "服务时长必须大于 0 分钟");
        }
        return startAt(appointDate, timeSlot).plusMinutes(durationMinutes);
    }

    /**
     * 当天时段已经开始或恰好到达开始时间时，视为不可预约。
     */
    public static boolean isStartPassed(LocalDate appointDate, String timeSlot, LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now 不能为空");
        }
        return !startAt(appointDate, timeSlot).isAfter(now);
    }

    public static boolean isStartPassed(LocalDateTime appointmentStart, LocalDateTime now) {
        if (appointmentStart == null || now == null) {
            throw new IllegalArgumentException("预约开始时间和当前时间不能为空");
        }
        return !appointmentStart.isAfter(now);
    }
}
