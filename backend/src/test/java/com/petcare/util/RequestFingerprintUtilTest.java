package com.petcare.util;

import com.petcare.dto.OrderCreateDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RequestFingerprintUtilTest {

    @Test
    void pipeCharactersCannotChangeFieldBoundaries() {
        OrderCreateDTO first = baseDto();
        first.setAddress("A|B");
        first.setRemark("C");
        OrderCreateDTO second = baseDto();
        second.setAddress("A");
        second.setRemark("B|C");

        assertNotEquals(
                RequestFingerprintUtil.of(2L, first),
                RequestFingerprintUtil.of(2L, second));
    }

    @Test
    void fingerprintMatchesStableLengthPrefixedCanonicalForm() {
        assertEquals(
                "306e5411d2d217db15826fde7c3f2a3113bf8b0b8a93a2191d5f48ec577bdb36",
                RequestFingerprintUtil.of(2L, baseDto()));
    }

    private OrderCreateDTO baseDto() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setPetId(1L);
        dto.setServiceId(3L);
        dto.setAppointDate(LocalDate.of(2026, 11, 30));
        dto.setTimeSlot("10:00-12:00");
        dto.setContactName("测试");
        dto.setContactPhone("13800000002");
        dto.setAddress("测试地址");
        dto.setRemark("测试备注");
        return dto;
    }
}
