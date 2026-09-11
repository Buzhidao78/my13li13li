-- ============================================================
-- practice2 数据库初始化脚本（结构权威文件，幂等可重复执行）
--
-- 执行方式：mysql -uroot -p123456 < schema.sql
-- 本文件只负责【建库 + 建表】，全部使用 CREATE ... IF NOT EXISTS，
-- 重复执行不会报错。造测试数据请再执行 backend/test-data.sql。
--
-- 适用场景：
--   1. 全新环境：直接执行本文件，所有表一次建齐；
--   2. 老库（user 表已存在但没有 member/role 列）：
--      先执行本文件（补建新增的表），再执行 backend/legacy-upgrade.sql 补列。
-- ============================================================

CREATE DATABASE IF NOT EXISTS practice2 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE practice2;

-- ============ 用户表 ============
-- 字段演进说明：member_level/member_expire（会员订单功能加入）、role（权限功能加入）
-- 全新库直接建全；老库缺列请执行 legacy-upgrade.sql
CREATE TABLE IF NOT EXISTS `user` (
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
CREATE TABLE IF NOT EXISTS member_order (
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
-- 模块1（视频上传与存储）应建的表；曾因脚本缺失只能手工建，现统一收编到本文件
CREATE TABLE IF NOT EXISTS video (
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
CREATE TABLE IF NOT EXISTS video_comment (
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
CREATE TABLE IF NOT EXISTS video_favorite (
    id          BIGINT   AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    video_id    BIGINT   NOT NULL COMMENT '收藏的视频',
    user_id     BIGINT   NOT NULL COMMENT '收藏的用户',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY uk_video_user (video_id, user_id) COMMENT '同一用户不能重复收藏',
    INDEX idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频收藏';

-- ============ 观看历史表 ============
CREATE TABLE IF NOT EXISTS watch_history (
    id          BIGINT   AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    video_id    BIGINT   NOT NULL COMMENT '看过的视频',
    user_id     BIGINT   NOT NULL COMMENT '观看用户',
    watch_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '观看时间',
    UNIQUE KEY uk_video_user (video_id, user_id) COMMENT '同一用户对同一视频只留一条历史',
    INDEX idx_user_time (user_id, watch_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '观看历史';

-- ============ 投币记录表 ============
CREATE TABLE IF NOT EXISTS video_coin (
    id          BIGINT   AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    video_id    BIGINT   NOT NULL COMMENT '被投币的视频',
    user_id     BIGINT   NOT NULL COMMENT '投币用户',
    coin        INT      NOT NULL DEFAULT 1 COMMENT '投币数量（每次1~2枚）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投币时间',
    INDEX idx_user (user_id, create_time),
    INDEX idx_video (video_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '投币记录';

-- ============ 用户关注关系表 ============
CREATE TABLE IF NOT EXISTS user_follow (
    id           BIGINT   AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    follower_id  BIGINT   NOT NULL COMMENT '关注者（操作方）',
    following_id BIGINT   NOT NULL COMMENT '被关注者（UP主）',
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    UNIQUE KEY uk_follow (follower_id, following_id) COMMENT '同一用户不能重复关注同一个人',
    INDEX idx_following (following_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户关注关系';

-- ============ 站内通知表 ============
CREATE TABLE IF NOT EXISTS user_notification (
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
CREATE TABLE IF NOT EXISTS dm_conversation (
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
CREATE TABLE IF NOT EXISTS dm_message (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    sender_id   BIGINT       NOT NULL COMMENT '发送者',
    receiver_id BIGINT       NOT NULL COMMENT '接收者',
    content     VARCHAR(500) NOT NULL COMMENT '消息内容（纯文本，前端文本插值渲染防XSS）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    INDEX idx_sender (sender_id, receiver_id, id) COMMENT '查"我发给谁的消息"（含抖音规则判定）',
    INDEX idx_receiver (receiver_id, sender_id, id) COMMENT '查"谁发给我的消息"'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '私信消息';
