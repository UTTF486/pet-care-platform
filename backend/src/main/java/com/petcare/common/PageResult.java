package com.petcare.common;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果对象。
 * 分页查询显式执行 count 与 limit，减少额外框架依赖。
 */
@Data
public class PageResult<T> implements Serializable {

    /** 总记录数 */
    private long total;
    /** 当前页码，从 1 开始 */
    private int pageNum;
    /** 每页条数 */
    private int pageSize;
    /** 当前页数据 */
    private List<T> list;

    public PageResult() {
    }

    public PageResult(long total, int pageNum, int pageSize, List<T> list) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.list = list == null ? Collections.emptyList() : list;
    }

    public static <T> PageResult<T> of(long total, int pageNum, int pageSize, List<T> list) {
        return new PageResult<>(total, pageNum, pageSize, list);
    }

    public static <T> PageResult<T> empty(int pageNum, int pageSize) {
        return new PageResult<>(0, pageNum, pageSize, Collections.emptyList());
    }
}
