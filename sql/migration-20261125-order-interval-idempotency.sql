-- 订单时间区间快照与用户级幂等（按顺序只执行一次）
USE pet_service;

ALTER TABLE t_order
    ADD COLUMN request_id VARCHAR(64) NULL COMMENT '客户端幂等请求号' AFTER order_no,
    ADD COLUMN service_duration INT NULL COMMENT '下单时服务时长快照(分钟)' AFTER amount,
    ADD COLUMN appointment_start DATETIME NULL COMMENT '预约开始时间快照' AFTER time_slot,
    ADD COLUMN appointment_end DATETIME NULL COMMENT '预约结束时间快照' AFTER appointment_start;

-- 历史订单用当前服务时长回填快照；完成后后续改服务时长不影响这些订单。
UPDATE t_order o
LEFT JOIN t_service s ON s.id = o.service_id
SET o.request_id = CONCAT('legacy-', o.id),
    o.service_duration = COALESCE(s.duration, 60),
    o.appointment_start = STR_TO_DATE(
        CONCAT(o.appoint_date, ' ', SUBSTRING_INDEX(o.time_slot, '-', 1)),
        '%Y-%m-%d %H:%i'
    ),
    o.appointment_end = DATE_ADD(
        STR_TO_DATE(
            CONCAT(o.appoint_date, ' ', SUBSTRING_INDEX(o.time_slot, '-', 1)),
            '%Y-%m-%d %H:%i'
        ),
        INTERVAL COALESCE(s.duration, 60) MINUTE
    );

ALTER TABLE t_order
    MODIFY COLUMN request_id VARCHAR(64) NOT NULL COMMENT '客户端幂等请求号',
    MODIFY COLUMN service_duration INT NOT NULL COMMENT '下单时服务时长快照(分钟)',
    MODIFY COLUMN appointment_start DATETIME NOT NULL COMMENT '预约开始时间快照',
    MODIFY COLUMN appointment_end DATETIME NOT NULL COMMENT '预约结束时间快照';

ALTER TABLE t_order
    ADD UNIQUE KEY uk_user_request (user_id, request_id),
    ADD INDEX idx_pet_id (pet_id),
    ADD INDEX idx_servicer_time (servicer_id, status, appointment_start, appointment_end);
