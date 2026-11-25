-- 高优先级修复增量索引（只执行一次，不要求重建数据库）
USE pet_service;

ALTER TABLE t_order
    ADD INDEX idx_servicer_schedule (servicer_id, appoint_date, time_slot, status);

ALTER TABLE t_review
    ADD INDEX idx_servicer_id (servicer_id);

ALTER TABLE t_message
    ADD INDEX idx_user_time (user_id, create_time);
