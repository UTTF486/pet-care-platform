-- 订单日志事件类型（只执行一次）
USE pet_service;

ALTER TABLE t_order_log
    ADD COLUMN event_type VARCHAR(32) NOT NULL DEFAULT 'STATUS_CHANGE'
    COMMENT '事件类型' AFTER to_status;

UPDATE t_order_log
SET event_type = 'URGED'
WHERE HEX(remark) = 'E794A8E688B7E582ACE58D95';

UPDATE t_order_log
SET event_type = 'ORDER_CREATED'
WHERE HEX(remark) = 'E794A8E688B7E68F90E4BAA4E9A284E7BAA6';
