-- 区间查询上线后，旧的日期 + 字符串时段索引不再被使用（只执行一次）
USE pet_service;

ALTER TABLE t_order
    DROP INDEX idx_servicer_schedule;
