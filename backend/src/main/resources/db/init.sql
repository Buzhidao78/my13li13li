-- ============================================================
-- 视频社区平台 数据库初始化脚本（唯一权威文件：建库 + 建表 + 初始数据）
-- 数据库：video_community（规范命名，utf8mb4）
--
-- 执行方式：mysql -uroot -p123456 < src/main/resources/db/init.sql
-- 特点：脚本开头 DROP DATABASE 全量重建，可重复执行（重复执行 = 重置到初始状态）
--
-- 账号体系（密码统一为 123456，BCrypt 加密）：
--   管理员  admin  / 123456  （账号密码登录，role=1）
--   用户    user002 / 123456 （手机号 13800000002）
--   用户    user003 / 123456 （手机号 13800000003）
--   用户    user004 / 123456 （手机号 13800000004）
--   用户    user005 / 123456 （手机号 13800000005）
-- 普通用户既可"用户名+密码"登录，也可"手机号+验证码"登录（验证码依赖 Redis）
-- ============================================================

DROP DATABASE IF EXISTS video_community;
CREATE DATABASE video_community DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE video_community;

-- 关键：显式声明导入连接字符集。否则 docker-entrypoint 以默认字符集（latin1）读入
-- UTF-8 文件，中文会被"双重编码"存库（如"用户"变成 ç"¨æˆ· 的字节），前端接口必然乱码。
-- 本地手动导入 `mysql -uroot -p < init.sql` 同样受益。
SET NAMES utf8mb4;

-- ============ 用户表 ============
CREATE TABLE `user` (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(50)           DEFAULT NULL COMMENT '用户名（账号密码登录用，可空）',
    password      VARCHAR(100)          DEFAULT NULL COMMENT '密码（BCrypt 加密；纯手机号验证码用户可为空）',
    phone         VARCHAR(20)           DEFAULT NULL COMMENT '手机号（验证码登录用）',
    nickname      VARCHAR(50)           DEFAULT NULL COMMENT '昵称',
    avatar        VARCHAR(255)          DEFAULT NULL COMMENT '头像地址',
    gender        TINYINT               DEFAULT 0 COMMENT '性别 0-未知 1-男 2-女',
    sign          VARCHAR(100)          DEFAULT NULL COMMENT '个性签名',
    member_level  TINYINT      NOT NULL DEFAULT 0 COMMENT '会员等级 0-普通 1-白银 2-黄金 3-钻石',
    member_expire DATETIME              DEFAULT NULL COMMENT '会员到期时间（NULL 表示从未开通）',
    role          TINYINT      NOT NULL DEFAULT 0 COMMENT '角色 0-普通用户 1-管理员（可审核/下架他人视频）',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '账号状态 0-正常 1-禁用',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-正常 1-删除',
    create_time   DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone)
) ENGINE = InnoDB COMMENT = '用户表';

-- ============ 会员充值订单表 ============
CREATE TABLE member_order (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id    VARCHAR(32)   NOT NULL COMMENT '业务订单号（全局唯一，Redis 幂等和 MQ 消息都以它为准）',
    user_id     BIGINT        NOT NULL COMMENT '下单用户 id',
    level       TINYINT       NOT NULL COMMENT '购买的会员等级 1-白银 2-黄金 3-钻石',
    amount      DECIMAL(10,2) NOT NULL COMMENT '订单金额（元）',
    days        INT           NOT NULL COMMENT '会员时长（天）',
    status      TINYINT       NOT NULL DEFAULT 0 COMMENT '订单状态 0-待支付 1-已支付 2-已关闭 3-已过期(延时关单)',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    pay_time    DATETIME      DEFAULT NULL COMMENT '支付时间',
    close_time  DATETIME      DEFAULT NULL COMMENT '关闭/过期时间',
    update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_id (order_id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB COMMENT = '会员充值订单表';

-- ============ 视频主表 ============
CREATE TABLE video (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT       NOT NULL COMMENT '上传用户 ID',
    title          VARCHAR(100) NOT NULL COMMENT '视频标题',
    description    VARCHAR(500)          DEFAULT '' COMMENT '视频简介',
    video_url      VARCHAR(255) NOT NULL COMMENT '视频文件访问路径（如 /upload/20260907/xxx.mp4）',
    cover_url      VARCHAR(255)          DEFAULT NULL COMMENT '封面访问路径（可选）',
    category       TINYINT      NOT NULL DEFAULT 0 COMMENT '分类：0默认 1生活 2游戏 3科技 4美食',
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0待审核 1已发布 2已驳回 3已下架',
    play_count     INT          NOT NULL DEFAULT 0 COMMENT '播放量',
    like_count     INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
    favorite_count INT          NOT NULL DEFAULT 0 COMMENT '收藏数',
    coin_count     INT          NOT NULL DEFAULT 0 COMMENT '投币数',
    create_time    DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_status_time (status, create_time),
    KEY idx_category_time (category, create_time)
) ENGINE = InnoDB COMMENT = '视频表';

-- ============ 视频评论表（楼中楼） ============
CREATE TABLE video_comment (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    video_id    BIGINT       NOT NULL COMMENT '所属视频',
    user_id     BIGINT       NOT NULL COMMENT '评论用户',
    content     VARCHAR(500) NOT NULL COMMENT '评论内容',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '回复的目标评论ID，0=一级评论',
    root_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '所属一级评论ID（楼中楼结构）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    INDEX idx_video_root (video_id, root_id),
    INDEX idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频评论';

-- ============ 视频收藏表 ============
CREATE TABLE video_favorite (
    id          BIGINT   AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    video_id    BIGINT   NOT NULL COMMENT '收藏的视频',
    user_id     BIGINT   NOT NULL COMMENT '收藏的用户',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY uk_video_user (video_id, user_id) COMMENT '同一用户不能重复收藏',
    INDEX idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频收藏';

-- ============ 观看历史表 ============
CREATE TABLE watch_history (
    id          BIGINT   AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    video_id    BIGINT   NOT NULL COMMENT '看过的视频',
    user_id     BIGINT   NOT NULL COMMENT '观看用户',
    watch_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '观看时间',
    UNIQUE KEY uk_video_user (video_id, user_id) COMMENT '同一用户对同一视频只留一条历史',
    INDEX idx_user_time (user_id, watch_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '观看历史';

-- ============ 投币记录表 ============
CREATE TABLE video_coin (
    id          BIGINT   AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    video_id    BIGINT   NOT NULL COMMENT '被投币的视频',
    user_id     BIGINT   NOT NULL COMMENT '投币用户',
    coin        INT      NOT NULL DEFAULT 1 COMMENT '投币数量（每次1~2枚）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投币时间',
    INDEX idx_user (user_id, create_time),
    INDEX idx_video (video_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '投币记录';

-- ============ 用户关注关系表 ============
CREATE TABLE user_follow (
    id           BIGINT   AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    follower_id  BIGINT   NOT NULL COMMENT '关注者（操作方）',
    following_id BIGINT   NOT NULL COMMENT '被关注者（UP主）',
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    UNIQUE KEY uk_follow (follower_id, following_id) COMMENT '同一用户不能重复关注同一个人',
    INDEX idx_following (following_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户关注关系';

-- ============ 站内通知表 ============
CREATE TABLE user_notification (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '接收者（被通知的人）',
    actor_id    BIGINT       NOT NULL COMMENT '触发者（谁点的赞）',
    type        TINYINT      NOT NULL COMMENT '通知类型 1点赞 2收藏 3投币 4评论 5回复 6关注',
    video_id    BIGINT                DEFAULT NULL COMMENT '关联视频ID（关注通知为空）',
    content     VARCHAR(200) NOT NULL COMMENT '通知摘要文本',
    is_read     TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '通知时间',
    INDEX idx_user_read (user_id, is_read)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内通知';

-- ============ 私信会话表（模块：私信 DM） ============
-- 设计说明：每个用户对每个对方存"一行自己的会话"，各存各的未读数与最后一条摘要。
-- 这样会话列表只需单表 WHERE user_id=我 ORDER BY update_time DESC，未读总数 SUM(unread_count)，
-- 无需 join/聚合；代价是发一条消息要写两行（写放大），练习项目完全可接受。
CREATE TABLE dm_conversation (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id         BIGINT       NOT NULL COMMENT '会话归属者（这一行是"谁的"会话）',
    peer_id         BIGINT       NOT NULL COMMENT '对方用户ID',
    last_message_id BIGINT                DEFAULT NULL COMMENT '最后一条消息ID',
    last_content    VARCHAR(100) NOT NULL DEFAULT '' COMMENT '最后一条消息摘要（截断100字）',
    last_time       DATETIME     NOT NULL COMMENT '最后一条消息时间',
    unread_count    INT          NOT NULL DEFAULT 0 COMMENT '我未读的对方消息数',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '会话创建时间',
    update_time     DATETIME     NOT NULL COMMENT '最后活跃时间（会话列表排序用，发消息时更新）',
    UNIQUE KEY uk_user_peer (user_id, peer_id) COMMENT '同一用户对同一对方只有一行会话',
    INDEX idx_user_time (user_id, update_time) COMMENT '会话列表查询索引'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '私信会话';

-- ============ 私信消息表 ============
CREATE TABLE dm_message (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    sender_id   BIGINT       NOT NULL COMMENT '发送者',
    receiver_id BIGINT       NOT NULL COMMENT '接收者',
    content     VARCHAR(500) NOT NULL COMMENT '消息内容（纯文本，前端文本插值渲染防XSS）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    INDEX idx_sender (sender_id, receiver_id, id) COMMENT '查"我发给谁的消息"（含抖音规则判定）',
    INDEX idx_receiver (receiver_id, sender_id, id) COMMENT '查"谁发给我的消息"'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '私信消息';

-- ============================================================
-- 初始数据（全量重建，ID 从 1 开始自增，稳定可预期）
-- ============================================================

-- 1. 用户（5 个账号；密码统一 123456，BCrypt 加密）
INSERT INTO user (username, password, phone, nickname, gender, status, role) VALUES
('admin',   '$2a$12$I.sJqRFXfjSGAzG7UHpPMO2MgbQ/iwgK5rKL/wN9R87n2sdmQiqx.', '13800000001', '管理员',   0, 0, 1),
('user0002', '$2a$12$I.sJqRFXfjSGAzG7UHpPMO2MgbQ/iwgK5rKL/wN9R87n2sdmQiqx.', '13800000002', '用户0002', 0, 0, 0),
('user0003', '$2a$12$I.sJqRFXfjSGAzG7UHpPMO2MgbQ/iwgK5rKL/wN9R87n2sdmQiqx.', '13800000003', '用户0003', 0, 0, 0),
('user0004', '$2a$12$I.sJqRFXfjSGAzG7UHpPMO2MgbQ/iwgK5rKL/wN9R87n2sdmQiqx.', '13800000004', '用户0004', 0, 0, 0),
('user0005', '$2a$12$I.sJqRFXfjSGAzG7UHpPMO2MgbQ/iwgK5rKL/wN9R87n2sdmQiqx.', '13800000005', '用户0005', 0, 0, 0);

-- 用户 ID 变量：后续所有关联数据按"用户名"解析，库重建也不会错位
SET @u2 = (SELECT id FROM user WHERE username = 'user0002');
SET @u3 = (SELECT id FROM user WHERE username = 'user0003');
SET @u4 = (SELECT id FROM user WHERE username = 'user0004');
SET @u5 = (SELECT id FROM user WHERE username = 'user0005');

-- 2. 会员演示数据：用户0002 已开通"黄金会员 30 天"（用户表字段 + 一张已支付订单）
UPDATE user SET member_level = 2, member_expire = DATE_ADD(NOW(), INTERVAL 30 DAY) WHERE username = 'user0002';
INSERT INTO member_order (order_id, user_id, level, amount, days, status, pay_time) VALUES
('SEED202609110001', @u2, 2, 29.90, 30, 1, NOW() - INTERVAL 2 DAY);

-- 3. 规范测试视频（12 条，ID 从 1 起按插入顺序排列）
--    作者分布：用户0002/0003/0004/0005
--    状态分布：9 条已发布(1)、2 条待审核(0)、1 条已驳回(2)
--    计数分布：热门/普通/新发布区分明显，方便测试推荐排序
--    video_url 复用模块1上传的真实文件（练习环境同一文件，播放器可播但内容相同）
INSERT INTO video (user_id, title, description, video_url, cover_url, category, status, play_count, like_count, favorite_count, coin_count, create_time) VALUES
(@u2, '我的第一个视频', '模块1上传的测试视频，作为规范数据保留。', '/upload/20260907/8818410d19984a56bda7b3cd48488db7.mp4', NULL, 1, 1, 233, 45, 12, 8, NOW() - INTERVAL 3 DAY),
(@u3, '家常红烧肉做法教程', '保姆级红烧肉教程，新手也能一次成功。', '/upload/20260907/8818410d19984a56bda7b3cd48488db7.mp4', NULL, 4, 1, 15200, 820, 310, 45, NOW() - INTERVAL 1 DAY),
(@u4, '《黑神话：悟空》通关实况 EP1', '第一视角通关实况，无剪辑完整版。', '/upload/20260907/8818410d19984a56bda7b3cd48488db7.mp4', NULL, 2, 1, 43000, 3100, 950, 260, NOW() - INTERVAL 5 HOUR),
(@u3, 'Java 多线程从入门到精通', '从线程创建到并发工具类的完整讲解。', '/upload/20260907/8818410d19984a56bda7b3cd48488db7.mp4', NULL, 3, 1, 21000, 1250, 620, 88, NOW() - INTERVAL 2 DAY),
(@u5, '周末 Vlog：爬山日记', '记录一次说走就走的爬山。', '/upload/20260907/8818410d19984a56bda7b3cd48488db7.mp4', NULL, 1, 1, 3800, 210, 95, 12, NOW() - INTERVAL 1 DAY),
(@u4, '红烧鱼的家常做法', '外酥里嫩的红烧鱼，关键在煎鱼这一步。', '/upload/20260907/8818410d19984a56bda7b3cd48488db7.mp4', NULL, 4, 1, 8600, 460, 180, 25, NOW() - INTERVAL 8 HOUR),
(@u5, '新手如何快速剪辑视频', '剪映零基础教学：转场、字幕、调色。', '/upload/20260907/8818410d19984a56bda7b3cd48488db7.mp4', NULL, 3, 1, 1500, 88, 40, 5, NOW() - INTERVAL 6 HOUR),
(@u3, '《艾尔登法环》BOSS 战合集', '全 BOSS 无伤击杀合集，含配装思路。', '/upload/20260907/8818410d19984a56bda7b3cd48488db7.mp4', NULL, 2, 1, 67000, 5200, 2100, 430, NOW() - INTERVAL 4 DAY),
(@u4, '2026 年装机指南：万元预算怎么配', '从 CPU 到显卡的完整选购思路。', '/upload/20260907/8818410d19984a56bda7b3cd48488db7.mp4', NULL, 3, 1, 12000, 700, 290, 36, NOW() - INTERVAL 3 HOUR),
(@u5, '城市夜景延时摄影', '30 秒看完一座城市的夜晚。', '/upload/20260907/8818410d19984a56bda7b3cd48488db7.mp4', NULL, 1, 0, 0, 0, 0, 0, NOW() - INTERVAL 30 MINUTE),
(@u3, '测试视频（待驳回）', '这条用于演示驳回状态。', '/upload/20260907/8818410d19984a56bda7b3cd48488db7.mp4', NULL, 0, 2, 0, 0, 0, 0, NOW() - INTERVAL 1 HOUR),
(@u4, '美食探店 Vlog：巷子里的宝藏小店', '藏在巷子里的 10 年老店。', '/upload/20260907/8818410d19984a56bda7b3cd48488db7.mp4', NULL, 4, 0, 0, 0, 0, 0, NOW() - INTERVAL 10 MINUTE);

-- 4. 评论数据（含楼中楼：parent_id/root_id 指向一级评论）
--    一致性设计：user0002 的"评论/回复"通知必须有真实落点，因此视频1（user0002 的
--    《我的第一个视频》）配了 3 条他人评论；视频2/3 保留社区讨论。
--    注：通知文案里的评论内容必须与这里的 content 完全一致，否则"点通知看视频"会落空。
INSERT INTO video_comment (video_id, user_id, content, parent_id, root_id, create_time) VALUES
-- 视频1（user0002 的《我的第一个视频》）：对应 4 号"评论通知"、5 号"回复通知"的落点
(1, @u4, '哈哈，学到了，这个剪辑手法很实用', 0, 0, NOW() - INTERVAL 4 HOUR),
(1, @u3, '前排围观，期待更多作品', 0, 0, NOW() - INTERVAL 3 HOUR),
(1, @u5, '支持一下', 0, 0, NOW() - INTERVAL 2 HOUR),
-- 视频2（user0003 的红烧肉教程）：@u2 提问、@u3 楼中楼回复（触发 user0002 的"回复通知"）
(2, @u4, '这个教程太详细了，跟着做成功了！', 0, 0, NOW() - INTERVAL 20 HOUR),
(2, @u2, 'up主请问酱油是生抽还是老抽？', 0, 0, NOW() - INTERVAL 18 HOUR),
(2, @u3, '回楼上：用的是生抽提鲜，老抽上色，教程里第3分钟有讲', 5, 5, NOW() - INTERVAL 17 HOUR),
(2, @u5, '收藏了，周末试试', 0, 0, NOW() - INTERVAL 12 HOUR),
-- 视频3（user0004 的黑神话实况）
(3, @u2, '这个BOSS打得太丝滑了', 0, 0, NOW() - INTERVAL 4 HOUR),
(3, @u5, '求个配装，我卡在这一关了', 0, 0, NOW() - INTERVAL 3 HOUR),
-- 视频5（user0005 的爬山 Vlog）：@u3 评论 → 通知 user0005
(5, @u3, '同城爬山搭子路过，下次一起！', 0, 0, NOW() - INTERVAL 2 HOUR);

-- 5. 收藏数据
--    一致性设计：每条收藏都对应一条"收藏通知"给视频作者；不存在作者收藏自己的视频
INSERT INTO video_favorite (video_id, user_id, create_time) VALUES
-- 视频2（user0003）被 user0005 收藏 → 通知 user0003
(2, @u5, NOW() - INTERVAL 20 HOUR),
-- 视频8（user0003）被 user0004 收藏 → 通知 user0003
(8, @u4, NOW() - INTERVAL 15 HOUR),
-- 视频1（user0002）被 user0005 收藏 → 通知 user0002
(1, @u5, NOW() - INTERVAL 3 HOUR),
-- 视频5（user0005）被 user0002 收藏 → 通知 user0005
(5, @u2, NOW() - INTERVAL 2 HOUR),
-- 视频3（user0004）被 user0002 收藏 → 通知 user0004
(3, @u2, NOW() - INTERVAL 1 HOUR);

-- 6. 观看历史
INSERT INTO watch_history (video_id, user_id, watch_time) VALUES
(2, @u2, NOW() - INTERVAL 18 HOUR),
(3, @u2, NOW() - INTERVAL 4 HOUR),
(4, @u2, NOW() - INTERVAL 1 DAY),
(8, @u2, NOW() - INTERVAL 2 DAY),
(2, @u3, NOW() - INTERVAL 10 HOUR);

-- 7. 投币记录（视频1 的 @u4 投币对应 user0002 的"投币通知"）
INSERT INTO video_coin (video_id, user_id, coin, create_time) VALUES
(2, @u2, 1, NOW() - INTERVAL 18 HOUR),
(8, @u2, 2, NOW() - INTERVAL 2 DAY),
(3, @u5, 1, NOW() - INTERVAL 3 HOUR),
(1, @u4, 1, NOW() - INTERVAL 2 HOUR);

-- 8. 关注关系：用户0002 与 0003/0004/0005 互相关注，方便测试动态流和粉丝列表
INSERT INTO user_follow (follower_id, following_id, create_time) VALUES
(@u2, @u3, NOW() - INTERVAL 3 DAY),
(@u2, @u4, NOW() - INTERVAL 2 DAY),
(@u3, @u2, NOW() - INTERVAL 1 DAY),
(@u4, @u2, NOW() - INTERVAL 20 HOUR),
(@u5, @u2, NOW() - INTERVAL 10 HOUR),
(@u3, @u4, NOW() - INTERVAL 5 HOUR);

-- 9. 站内通知：四个用户的通知全覆盖
--    一致性设计（有迹可查）：每条通知都有真实数据落点——
--      评论(4)/回复(5) → video_comment 有对应评论；
--      收藏(2) → video_favorite 有记录；投币(3) → video_coin 有记录；
--      关注(6) → user_follow 有记录；
--      点赞(1) 的点赞状态存于 Redis，MySQL 无落库，属设计内约定。
--    user_id 必须是视频作者/被关注人，文案与落点内容完全一致，点开即能看到对应互动。
INSERT INTO user_notification (user_id, actor_id, type, video_id, content, is_read, create_time) VALUES
-- ===== 给 user0002（视频1《我的第一个视频》作者）=====
-- 点赞（点赞状态在 Redis）
(@u2, @u3, 1, 1, '用户0003 赞了你的视频《我的第一个视频》', 0, NOW() - INTERVAL 5 HOUR),
-- 三条真实评论（视频1 上确有对应评论）
(@u2, @u4, 4, 1, '用户0004 评论了你的视频《我的第一个视频》：哈哈，学到了，这个剪辑手法很实用', 0, NOW() - INTERVAL 4 HOUR),
(@u2, @u3, 4, 1, '用户0003 评论了你的视频《我的第一个视频》：前排围观，期待更多作品', 0, NOW() - INTERVAL 3 HOUR),
(@u2, @u5, 4, 1, '用户0005 评论了你的视频《我的第一个视频》：支持一下', 0, NOW() - INTERVAL 2 HOUR),
-- 收藏/投币落点
(@u2, @u5, 2, 1, '用户0005 收藏了你的视频《我的第一个视频》', 1, NOW() - INTERVAL 3 HOUR),
(@u2, @u4, 3, 1, '用户0004 给你投了币：《我的第一个视频》', 0, NOW() - INTERVAL 2 HOUR),
-- 楼中楼回复（视频2 上 user0003 回复了 user0002 的提问）
(@u2, @u3, 5, 2, '用户0003 回复了你的评论：用的是生抽提鲜，老抽上色，教程里第3分钟有讲', 0, NOW() - INTERVAL 16 HOUR),
-- 关注（user0003/0004/0005 确实关注了 user0002）
(@u2, @u3, 6, NULL, '用户0003 关注了你', 0, NOW() - INTERVAL 1 DAY),
(@u2, @u4, 6, NULL, '用户0004 关注了你', 0, NOW() - INTERVAL 20 HOUR),
(@u2, @u5, 6, NULL, '用户0005 关注了你', 0, NOW() - INTERVAL 10 HOUR),
-- ===== 给 user0003（视频2《家常红烧肉做法教程》/视频8《艾尔登法环》作者）=====
-- 视频2 上的三条真实评论
(@u3, @u4, 4, 2, '用户0004 评论了你的视频《家常红烧肉做法教程》：这个教程太详细了，跟着做成功了！', 0, NOW() - INTERVAL 20 HOUR),
(@u3, @u2, 4, 2, '用户0002 评论了你的视频《家常红烧肉做法教程》：up主请问酱油是生抽还是老抽？', 0, NOW() - INTERVAL 18 HOUR),
(@u3, @u5, 4, 2, '用户0005 评论了你的视频《家常红烧肉做法教程》：收藏了，周末试试', 0, NOW() - INTERVAL 12 HOUR),
-- 收藏/投币落点
(@u3, @u5, 2, 2, '用户0005 收藏了你的视频《家常红烧肉做法教程》', 0, NOW() - INTERVAL 20 HOUR),
(@u3, @u4, 2, 8, '用户0004 收藏了你的视频《《艾尔登法环》BOSS 战合集》', 0, NOW() - INTERVAL 15 HOUR),
(@u3, @u2, 3, 2, '用户0002 给你投了币：《家常红烧肉做法教程》', 0, NOW() - INTERVAL 18 HOUR),
(@u3, @u2, 3, 8, '用户0002 给你投了币：《《艾尔登法环》BOSS 战合集》', 0, NOW() - INTERVAL 2 DAY),
-- 关注（user0002 关注了 user0003）
(@u3, @u2, 6, NULL, '用户0002 关注了你', 0, NOW() - INTERVAL 3 DAY),
-- ===== 给 user0004（视频3《黑神话》作者）=====
-- 视频3 上的两条真实评论
(@u4, @u2, 4, 3, '用户0002 评论了你的视频《《黑神话：悟空》通关实况 EP1》：这个BOSS打得太丝滑了', 0, NOW() - INTERVAL 4 HOUR),
(@u4, @u5, 4, 3, '用户0005 评论了你的视频《《黑神话：悟空》通关实况 EP1》：求个配装，我卡在这一关了', 0, NOW() - INTERVAL 3 HOUR),
-- 收藏/投币落点
(@u4, @u2, 2, 3, '用户0002 收藏了你的视频《《黑神话：悟空》通关实况 EP1》', 0, NOW() - INTERVAL 1 HOUR),
(@u4, @u5, 3, 3, '用户0005 给你投了币：《《黑神话：悟空》通关实况 EP1》', 0, NOW() - INTERVAL 3 HOUR),
-- 关注（user0002、user0003 关注了 user0004）
(@u4, @u2, 6, NULL, '用户0002 关注了你', 0, NOW() - INTERVAL 2 DAY),
(@u4, @u3, 6, NULL, '用户0003 关注了你', 0, NOW() - INTERVAL 5 HOUR),
-- ===== 给 user0005（视频5《周末 Vlog：爬山日记》作者）=====
-- 视频5 上的一条真实评论 + 收藏落点
(@u5, @u3, 4, 5, '用户0003 评论了你的视频《周末 Vlog：爬山日记》：同城爬山搭子路过，下次一起！', 0, NOW() - INTERVAL 2 HOUR),
(@u5, @u2, 2, 5, '用户0002 收藏了你的视频《周末 Vlog：爬山日记》', 0, NOW() - INTERVAL 2 HOUR);

-- 10. 私信会话与消息（模块：私信 DM）
--     设计三对会话，覆盖三种验收场景：
--     ① 0002↔0003 互相关注 → 聊天自由来回，0002 侧有 2 条未读（验收红点）
--     ② 0002↔0004 互相关注 → 0002 侧有 1 条未读（验收红点合并）
--     ③ 0002→0005 非互关（0005→0002 单向关注）→ 0002 已发一条未获回复
--        （验收抖音规则：0002 再发被拒；0005 回复后 0002 可再发）
INSERT INTO dm_message (sender_id, receiver_id, content, create_time) VALUES
(@u2, @u3, '在吗？红烧肉教程第3步那个小火是多久', NOW() - INTERVAL 5 HOUR),
(@u3, @u2, '在的！小火慢炖 40 分钟，中途别揭盖', NOW() - INTERVAL 4 HOUR),
(@u2, @u3, '收到，我周末试试，失败了再来问你', NOW() - INTERVAL 3 HOUR),
(@u3, @u2, '哈哈好，期待你的成品', NOW() - INTERVAL 50 MINUTE),
(@u3, @u2, '对了，上次的截图能发我看看吗', NOW() - INTERVAL 49 MINUTE),
(@u4, @u2, '你那个装机视频里用的什么机箱？', NOW() - INTERVAL 2 DAY),
(@u2, @u4, '联力的包豪斯，走线很方便', NOW() - INTERVAL 2 DAY + INTERVAL 1 HOUR),
(@u4, @u2, '谢啦，我也想入一个', NOW() - INTERVAL 30 MINUTE),
(@u2, @u5, '你好，看了你爬山的 Vlog 想交流一下拍摄设备', NOW() - INTERVAL 8 HOUR);

INSERT INTO dm_conversation (user_id, peer_id, last_message_id, last_content, last_time, unread_count, create_time, update_time) VALUES
-- 会话① 0002 的行：对方(0003)最后一条是"对了…截图"，0002 未读 2 条
(@u2, @u3, (SELECT id FROM dm_message WHERE content = '对了，上次的截图能发我看看吗'),
 '对了，上次的截图能发我看看吗', NOW() - INTERVAL 49 MINUTE, 2, NOW() - INTERVAL 5 HOUR, NOW() - INTERVAL 49 MINUTE),
-- 会话① 0003 的行：0002 的最后一条已读
(@u3, @u2, (SELECT id FROM dm_message WHERE content = '收到，我周末试试，失败了再来问你'),
 '收到，我周末试试，失败了再来问你', NOW() - INTERVAL 3 HOUR, 0, NOW() - INTERVAL 5 HOUR, NOW() - INTERVAL 3 HOUR),
-- 会话② 0002 的行：0004 最后一条未读
(@u2, @u4, (SELECT id FROM dm_message WHERE content = '谢啦，我也想入一个'),
 '谢啦，我也想入一个', NOW() - INTERVAL 30 MINUTE, 1, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 30 MINUTE),
-- 会话② 0004 的行
(@u4, @u2, (SELECT id FROM dm_message WHERE content = '联力的包豪斯，走线很方便'),
 '联力的包豪斯，走线很方便', NOW() - INTERVAL 2 DAY + INTERVAL 1 HOUR, 0, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY + INTERVAL 1 HOUR),
-- 会话③ 0002 的行：我发的一条已读（0005 没回，抖音规则限制 0002 再发）
(@u2, @u5, (SELECT id FROM dm_message WHERE content LIKE '你好，看了你爬山%'),
 '你好，看了你爬山的 Vlog 想交流一下拍摄设备', NOW() - INTERVAL 8 HOUR, 0, NOW() - INTERVAL 8 HOUR, NOW() - INTERVAL 8 HOUR),
-- 会话③ 0005 的行：未读 1
(@u5, @u2, (SELECT id FROM dm_message WHERE content LIKE '你好，看了你爬山%'),
 '你好，看了你爬山的 Vlog 想交流一下拍摄设备', NOW() - INTERVAL 8 HOUR, 1, NOW() - INTERVAL 8 HOUR, NOW() - INTERVAL 8 HOUR);
