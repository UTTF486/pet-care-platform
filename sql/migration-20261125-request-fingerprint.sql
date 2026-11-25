-- 幂等请求参数指纹（只执行一次）
USE pet_service;

ALTER TABLE t_order
    ADD COLUMN request_hash VARCHAR(64) NULL COMMENT '幂等请求参数指纹' AFTER request_id;

UPDATE t_order
SET request_hash = SHA2(CONCAT('legacy:', order_no), 256)
WHERE request_hash IS NULL;

ALTER TABLE t_order
    MODIFY COLUMN request_hash VARCHAR(64) NOT NULL COMMENT '幂等请求参数指纹';
