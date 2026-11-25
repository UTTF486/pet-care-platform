-- 统一历史订单占位指纹，使全量种子与增量升级结果一致（只执行一次）
USE pet_service;

-- legacy requestId 仅用于历史数据占位，不支持客户端幂等重放。
-- 客户端不得使用 legacy- 前缀作为真实 requestId；这里用 id 精确匹配历史种子行。
UPDATE t_order
SET request_hash = SHA2(CONCAT('legacy:', order_no), 256)
WHERE request_id = CONCAT('legacy-order-', id);
