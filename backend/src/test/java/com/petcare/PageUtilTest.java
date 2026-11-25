package com.petcare;

import com.petcare.util.PageUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageUtilTest {

    @Test
    void normalizesInvalidPageArguments() {
        assertEquals(1, PageUtil.normalizePageNum(0));
        assertEquals(1, PageUtil.normalizePageNum(-10));
        assertEquals(1, PageUtil.normalizePageSize(0));
        assertEquals(PageUtil.MAX_PAGE_SIZE, PageUtil.normalizePageSize(10_000));
        assertEquals(0, PageUtil.offset(-1, 20));
    }
}
