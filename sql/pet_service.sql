-- =====================================================================
-- 智慧宠物服务上门平台  数据库脚本
-- MySQL 8.0+ / utf8mb4
-- 使用方式：mysql -uroot -p < pet_service.sql
-- =====================================================================
DROP DATABASE IF EXISTS pet_service;
CREATE DATABASE pet_service DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE pet_service;

-- ---------------------------------------------------------------------
-- 1. 用户表：一张表承载「普通用户」和「商家管理员」两种角色
--    role=0 普通用户(下单方)  role=1 商家管理员(接单/管理后台)
--    status=1 正常  0 禁用(禁用后无法登录，Token 立即失效由拦截器校验)
-- ---------------------------------------------------------------------
CREATE TABLE t_user
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(32)  NOT NULL COMMENT '登录账号',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码(不存明文)',
    nickname    VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '昵称',
    phone       VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '手机号',
    avatar      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '头像地址',
    role        TINYINT      NOT NULL DEFAULT 0 COMMENT '角色 0-普通用户 1-商家管理员',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1-正常 0-禁用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username) COMMENT '账号唯一，注册时靠它兜底防重复'
) ENGINE = InnoDB COMMENT '用户表';

-- ---------------------------------------------------------------------
-- 2. 宠物档案表：一个用户可以有多只宠物，下单时选择其中一只
-- ---------------------------------------------------------------------
CREATE TABLE t_pet
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT      NOT NULL COMMENT '所属用户 id',
    name        VARCHAR(32) NOT NULL COMMENT '宠物名',
    pet_type    VARCHAR(16) NOT NULL DEFAULT '猫' COMMENT '类型：猫/狗/其他',
    breed       VARCHAR(32) NOT NULL DEFAULT '' COMMENT '品种，如英短/柯基',
    gender      TINYINT     NOT NULL DEFAULT 0 COMMENT '性别 0-未知 1-公 2-母',
    age         INT         NOT NULL DEFAULT 0 COMMENT '年龄(月)',
    weight      DECIMAL(5, 2)        DEFAULT NULL COMMENT '体重(kg)',
    avatar      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '头像',
    remark      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '备注：怕生/肠胃敏感/需喂药等，服务师要看',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id) COMMENT '按用户查档案，高频'
) ENGINE = InnoDB COMMENT '宠物档案表';

-- ---------------------------------------------------------------------
-- 3. 服务品类表：上门喂宠 / 洗护 / 遛狗 ...
-- ---------------------------------------------------------------------
CREATE TABLE t_category
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(32) NOT NULL COMMENT '品类名',
    icon        VARCHAR(32) NOT NULL DEFAULT '' COMMENT '图标标识，前端映射 emoji 或 iconfont',
    sort        INT         NOT NULL DEFAULT 0 COMMENT '排序，越小越前',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT '服务品类表';

-- ---------------------------------------------------------------------
-- 4. 服务项目表：具体可下单的商品，价格/时长/上下架
--    sales 冗余存储销量，避免统计时反复 count 订单表
-- ---------------------------------------------------------------------
CREATE TABLE t_service
(
    id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_id BIGINT         NOT NULL COMMENT '所属品类',
    name        VARCHAR(64)    NOT NULL COMMENT '服务名',
    cover       VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '封面图',
    description VARCHAR(500)   NOT NULL DEFAULT '' COMMENT '服务说明',
    price       DECIMAL(10, 2) NOT NULL COMMENT '单价(元)',
    duration    INT            NOT NULL DEFAULT 60 COMMENT '服务时长(分钟)',
    status      TINYINT        NOT NULL DEFAULT 1 COMMENT '1-上架 0-下架',
    sales       INT            NOT NULL DEFAULT 0 COMMENT '累计销量(冗余字段)',
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_category_status (category_id, status) COMMENT '列表页按品类+上架状态过滤'
) ENGINE = InnoDB COMMENT '服务项目表';

-- ---------------------------------------------------------------------
-- 5. 服务师表：接单的人，后台维护技能/等级/评分
-- ---------------------------------------------------------------------
CREATE TABLE t_servicer
(
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(32)   NOT NULL COMMENT '姓名',
    phone       VARCHAR(20)   NOT NULL DEFAULT '' COMMENT '联系电话',
    avatar      VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '头像',
    skills      VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '擅长技能，逗号分隔',
    level       TINYINT       NOT NULL DEFAULT 1 COMMENT '等级 1-初级 2-中级 3-高级',
    rating      DECIMAL(3, 2) NOT NULL DEFAULT 5.00 COMMENT '服务评分(0-5)',
    order_count INT           NOT NULL DEFAULT 0 COMMENT '累计接单数',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '1-可接单 0-休息',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT '服务师表';

-- ---------------------------------------------------------------------
-- 6. 订单表(核心)
--    status: 0-待接单 1-待服务(已接单) 2-已完成 3-已取消
--    expire_time: 超时自动取消的时间点，定时任务扫它
--    service_name / service_price 冗余快照：服务改价后历史订单金额不能被篡改
-- ---------------------------------------------------------------------
CREATE TABLE t_order
(
    id            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no      VARCHAR(32)    NOT NULL COMMENT '业务订单号，对外展示',
    user_id       BIGINT         NOT NULL COMMENT '下单用户',
    pet_id        BIGINT         NOT NULL COMMENT '服务对象宠物',
    service_id    BIGINT         NOT NULL COMMENT '服务项目',
    servicer_id   BIGINT                  DEFAULT NULL COMMENT '接单服务师，未接单为 NULL',
    service_name  VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '服务名快照',
    service_price DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '下单时单价快照',
    amount        DECIMAL(10, 2) NOT NULL COMMENT '实付金额',
    contact_name  VARCHAR(32)    NOT NULL COMMENT '联系人',
    contact_phone VARCHAR(20)    NOT NULL COMMENT '联系电话',
    address       VARCHAR(255)   NOT NULL COMMENT '上门地址',
    appoint_date  DATE           NOT NULL COMMENT '预约日期',
    time_slot     VARCHAR(32)    NOT NULL COMMENT '预约时段，如 10:00-12:00',
    status        TINYINT        NOT NULL DEFAULT 0 COMMENT '0-待接单 1-待服务 2-已完成 3-已取消',
    remark        VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '用户备注',
    cancel_reason VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '取消原因',
    create_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_time   DATETIME       NOT NULL COMMENT '超时未接单的自动取消时间',
    accept_time   DATETIME                DEFAULT NULL COMMENT '接单时间',
    finish_time   DATETIME                DEFAULT NULL COMMENT '完成时间',
    cancel_time   DATETIME                DEFAULT NULL COMMENT '取消时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_status (user_id, status, create_time) COMMENT '用户端我的订单',
    KEY idx_status_expire (status, expire_time) COMMENT '定时任务扫超时单，避免全表扫',
    KEY idx_create_time (create_time) COMMENT '报表按时间范围聚合'
) ENGINE = InnoDB COMMENT '服务订单表';

-- ---------------------------------------------------------------------
-- 7. 订单流转日志：每次状态变更留痕，方便售后追溯 & 面试讲"可审计"
-- ---------------------------------------------------------------------
CREATE TABLE t_order_log
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    order_id      BIGINT      NOT NULL COMMENT '订单 id',
    from_status   TINYINT     NOT NULL COMMENT '变更前状态',
    to_status     TINYINT     NOT NULL COMMENT '变更后状态',
    operator_type TINYINT     NOT NULL DEFAULT 0 COMMENT '0-用户 1-商家 2-系统',
    operator_id   BIGINT      NOT NULL DEFAULT 0 COMMENT '操作人 id，系统操作为 0',
    remark        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '操作说明',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id)
) ENGINE = InnoDB COMMENT '订单流转日志表';

-- ---------------------------------------------------------------------
-- 8. 评价表：一单一评，用来算好评率(rating>=4 视为好评)
-- ---------------------------------------------------------------------
CREATE TABLE t_review
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    BIGINT       NOT NULL COMMENT '订单 id',
    user_id     BIGINT       NOT NULL,
    service_id  BIGINT       NOT NULL,
    servicer_id BIGINT                DEFAULT NULL,
    rating      TINYINT      NOT NULL COMMENT '1-5 星',
    content     VARCHAR(500) NOT NULL DEFAULT '',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_id (order_id) COMMENT '一单一评，数据库层防重复提交',
    KEY idx_service_id (service_id)
) ENGINE = InnoDB COMMENT '服务评价表';

-- ---------------------------------------------------------------------
-- 9. 消息表：催单通知 / 状态变更通知
--    user_id = 0 表示发给商家后台(商家信箱)
-- ---------------------------------------------------------------------
CREATE TABLE t_message
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL COMMENT '接收人 id，0 表示商家后台',
    order_id    BIGINT                DEFAULT NULL COMMENT '关联订单',
    type        VARCHAR(16)  NOT NULL DEFAULT 'SYSTEM' COMMENT 'ORDER-订单通知 URGE-催单 SYSTEM-系统',
    title       VARCHAR(64)  NOT NULL DEFAULT '',
    content     VARCHAR(500) NOT NULL DEFAULT '',
    is_read     TINYINT      NOT NULL DEFAULT 0 COMMENT '0-未读 1-已读',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_read (user_id, is_read)
) ENGINE = InnoDB COMMENT '消息通知表';

-- =====================================================================
-- 种子数据
-- 说明：密码先写明文 123456，应用启动时 DataInitializer 会自动把它
--      升级成 BCrypt 密文（这样脚本里不用塞 bcrypt 串，避免抄错）
-- =====================================================================
INSERT INTO t_user (username, password, nickname, phone, role, status)
VALUES ('admin', '123456', '店长小张', '13800000001', 1, 1),
       ('demo', '123456', '铲屎官小李', '13800000002', 0, 1),
       ('lily', '123456', '布偶妈妈', '13800000003', 0, 1),
       ('tom', '123456', '柯基爸爸', '13800000004', 0, 1);

INSERT INTO t_pet (user_id, name, pet_type, breed, gender, age, weight, remark)
VALUES (2, '奶糖', '猫', '布偶', 2, 14, 3.60, '怕生，进门先别抱'),
       (2, '豆包', '狗', '柯基', 1, 20, 11.20, '精力旺盛，需要遛 40 分钟'),
       (3, '雪球', '猫', '英短', 2, 8, 2.80, '肠胃敏感，只吃自带粮'),
       (4, '可乐', '狗', '柴犬', 1, 18, 9.50, '有点护食，喂食时注意');

INSERT INTO t_category (name, icon, sort, status)
VALUES ('上门喂宠', 'feed', 1, 1),
       ('上门洗护', 'wash', 2, 1),
       ('遛狗服务', 'walk', 3, 1),
       ('健康陪护', 'care', 4, 1);

INSERT INTO t_service (category_id, name, cover, description, price, duration, status, sales)
VALUES (1, '猫咪上门喂养·单次', '', '添粮换水 + 铲屎 + 陪玩 20 分钟，全程拍照反馈', 49.00, 40, 1, 128),
       (1, '多宠家庭喂养·单次', '', '2-3 只宠物，含喂药、清理猫砂盆', 79.00, 60, 1, 56),
       (2, '上门洗澡·小型犬', '', '含吹干、修剪指甲、清理耳道，自带专业设备', 128.00, 90, 1, 74),
       (2, '上门洗澡·猫咪', '', '温和手法，防应激，含梳毛', 158.00, 90, 1, 43),
       (3, '遛狗 30 分钟', '', '小区周边安全路线，实时定位 + 视频反馈', 39.00, 40, 1, 201),
       (3, '遛狗 60 分钟', '', '含户外互动、饮水补给', 69.00, 70, 1, 87),
       (4, '术后/老年宠物陪护', '', '喂药、伤口观察、体征记录，持证护理师', 199.00, 120, 1, 21),
       (4, '宠物上门体检', '', '基础体征检查 + 健康报告建议', 259.00, 120, 0, 9);

INSERT INTO t_servicer (name, phone, skills, level, rating, order_count, status)
VALUES ('王阿姨', '13900000001', '猫咪喂养,铲屎,喂药', 2, 4.90, 156, 1),
       ('陈师傅', '13900000002', '大型犬洗护,遛狗', 3, 4.80, 203, 1),
       ('小林', '13900000003', '猫咪洗护,陪护', 1, 4.60, 62, 1),
       ('赵医生', '13900000004', '术后护理,体征监测', 3, 5.00, 38, 0);

-- 订单：分布在最近 7 天，让数据看板一打开就有图
INSERT INTO t_order (order_no, user_id, pet_id, service_id, servicer_id, service_name, service_price, amount,
                     contact_name, contact_phone, address, appoint_date, time_slot, status, remark,
                     create_time, expire_time, accept_time, finish_time)
VALUES ('PC20261110001', 2, 1, 1, 1, '猫咪上门喂养·单次', 49.00, 49.00, '小李', '13800000002',
        '杭州市西湖区文三路 100 号 3-502', DATE_SUB(CURDATE(), INTERVAL 6 DAY), '10:00-12:00', 2, '记得拍视频',
        DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY),
        DATE_SUB(NOW(), INTERVAL 6 DAY)),
       ('PC20261111002', 3, 3, 4, 3, '上门洗澡·猫咪', 158.00, 158.00, 'lily', '13800000003',
        '杭州市余杭区未来科技城 8 幢 1201', DATE_SUB(CURDATE(), INTERVAL 5 DAY), '14:00-16:00', 2, '',
        DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY),
        DATE_SUB(NOW(), INTERVAL 5 DAY)),
       ('PC20261112003', 2, 2, 6, 2, '遛狗 60 分钟', 69.00, 69.00, '小李', '13800000002',
        '杭州市西湖区文三路 100 号', DATE_SUB(CURDATE(), INTERVAL 4 DAY), '18:00-20:00', 2, '',
        DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY),
        DATE_SUB(NOW(), INTERVAL 4 DAY)),
       ('PC20261113004', 4, 4, 5, 2, '遛狗 30 分钟', 39.00, 39.00, 'tom', '13800000004',
        '杭州市滨江区江南大道 200 号', DATE_SUB(CURDATE(), INTERVAL 3 DAY), '08:00-10:00', 3, '临时出差',
        DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
       ('PC20261114005', 3, 3, 1, 1, '猫咪上门喂养·单次', 49.00, 49.00, 'lily', '13800000003',
        '杭州市余杭区未来科技城 8 幢 1201', DATE_SUB(CURDATE(), INTERVAL 2 DAY), '10:00-12:00', 2, '',
        DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY),
        DATE_SUB(NOW(), INTERVAL 2 DAY)),
       ('PC20261115006', 4, 4, 3, 3, '上门洗澡·小型犬', 128.00, 128.00, 'tom', '13800000004',
        '杭州市滨江区江南大道 200 号', DATE_SUB(CURDATE(), INTERVAL 1 DAY), '16:00-18:00', 2, '',
        DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY),
        DATE_SUB(NOW(), INTERVAL 1 DAY)),
       ('PC20261116007', 2, 1, 2, 1, '多宠家庭喂养·单次', 79.00, 79.00, '小李', '13800000002',
        '杭州市西湖区文三路 100 号 3-502', DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:00-12:00', 1, '两只猫都要喂',
        DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 1 HOUR),
        NULL),
       ('PC20261116008', 4, 4, 5, NULL, '遛狗 30 分钟', 39.00, 39.00, 'tom', '13800000004',
        '杭州市滨江区江南大道 200 号', CURDATE(), '18:00-20:00', 0, '尽量安排有经验的师傅',
        DATE_SUB(NOW(), INTERVAL 10 MINUTE), DATE_ADD(NOW(), INTERVAL 30 MINUTE), NULL, NULL);

INSERT INTO t_order_log (order_id, from_status, to_status, operator_type, operator_id, remark)
VALUES (1, 0, 1, 1, 1, '商家接单'),
       (1, 1, 2, 1, 1, '服务完成'),
       (4, 0, 3, 0, 4, '用户取消：临时出差'),
       (7, 0, 1, 1, 1, '商家接单，指派王阿姨'),
       (8, 0, 0, 0, 4, '用户催单');

INSERT INTO t_review (order_id, user_id, service_id, servicer_id, rating, content)
VALUES (1, 2, 1, 1, 5, '王阿姨很细心，猫粮和视频都拍得很清楚'),
       (2, 3, 4, 3, 5, '手法很轻柔，猫没有应激'),
       (3, 2, 6, 2, 4, '遛得很足，就是稍微晚到了 5 分钟'),
       (5, 3, 1, 1, 5, '第二次下单了，稳定靠谱'),
       (6, 4, 3, 3, 3, '洗得还可以，吹干不太彻底');

INSERT INTO t_message (user_id, order_id, type, title, content, is_read)
VALUES (2, 1, 'ORDER', '服务已完成', '订单 PC20261110001 已完成，快去评价吧～', 1),
       (0, 8, 'URGE', '用户催单', '订单 PC20261116008 用户已催单，请尽快接单', 0),
       (2, 7, 'ORDER', '商家已接单', '订单 PC20261116007 已指派服务师王阿姨', 0);
