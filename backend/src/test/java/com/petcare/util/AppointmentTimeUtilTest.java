package com.petcare.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentTimeUtilTest {

    @Test
    void futureStartIsAllowed() {
        LocalDate date = LocalDate.of(2026, 11, 26);
        assertFalse(AppointmentTimeUtil.isStartPassed(
                date, "10:00-12:00", LocalDateTime.of(2026, 11, 25, 18, 0)));
    }

    @Test
    void startedOrExactlyAtStartIsRejected() {
        LocalDate today = LocalDate.of(2026, 11, 25);
        assertTrue(AppointmentTimeUtil.isStartPassed(
                today, "10:00-12:00", LocalDateTime.of(2026, 11, 25, 10, 0)));
        assertTrue(AppointmentTimeUtil.isStartPassed(
                today, "10:00-12:00", LocalDateTime.of(2026, 11, 25, 10, 1)));
    }

    @Test
    void laterSlotOnSameDayIsAllowed() {
        LocalDate today = LocalDate.of(2026, 11, 25);
        assertFalse(AppointmentTimeUtil.isStartPassed(
                today, "18:00-20:00", LocalDateTime.of(2026, 11, 25, 17, 59)));
    }

    @Test
    void durationProducesCrossSlotEndTime() {
        assertEquals(
                LocalDateTime.of(2026, 11, 25, 12, 0),
                AppointmentTimeUtil.endAt(
                        LocalDate.of(2026, 11, 25), "10:00-12:00", 120));
    }
}
