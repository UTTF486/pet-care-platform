package com.petcare.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderNoGeneratorTest {

    @Test
    void orderNumberHasStableFormatAndFitsDatabaseColumn() {
        String orderNo = OrderNoGenerator.build(
                LocalDateTime.of(2026, 11, 25, 18, 30, 45, 123_000_000), 12);

        assertEquals("PC2026112518304512300000000000c", orderNo);
        assertEquals(31, orderNo.length());
        assertTrue(orderNo.matches("^PC\\d{17}[0-9a-z]{12}$"));
    }
}
