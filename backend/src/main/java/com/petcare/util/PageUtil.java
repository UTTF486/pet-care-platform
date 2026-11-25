package com.petcare.util;

import com.petcare.common.BusinessException;
import com.petcare.common.ResultCode;

/**
 * 统一分页参数边界，避免负数 offset 和大页查询拖垮数据库。
 */
public final class PageUtil {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_PAGE_NUM = 1000;

    private PageUtil() {
    }

    public static int normalizePageNum(int pageNum) {
        if (pageNum > MAX_PAGE_NUM) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "页码不能超过 " + MAX_PAGE_NUM);
        }
        return Math.max(pageNum, 1);
    }

    public static int normalizePageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }

    public static int offset(int pageNum, int pageSize) {
        long offset = (long) (normalizePageNum(pageNum) - 1) * normalizePageSize(pageSize);
        return offset > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) offset;
    }
}
