-- 后台列表前缀/精确搜索索引（只执行一次）
USE pet_service;

ALTER TABLE t_user
    ADD INDEX idx_phone (phone),
    ADD INDEX idx_nickname (nickname);

ALTER TABLE t_service
    ADD INDEX idx_name (name);

ALTER TABLE t_servicer
    ADD INDEX idx_name (name),
    ADD INDEX idx_phone (phone);

ALTER TABLE t_order
    ADD INDEX idx_contact_name (contact_name),
    ADD INDEX idx_contact_phone (contact_phone),
    ADD INDEX idx_address (address);
