-- ============================================================
-- 老库升级脚本（仅"老库"需要执行，全新库【不要】执行）
--
-- 场景：在"会员订单/权限"功能之前就建过 user 表的旧数据库，
--       表中缺少 member_level / member_expire / role 三个字段。
--       先执行 db/schema.sql（补齐新增表），再执行本文件补列。
-- 注意：本文件重复执行会因"列已存在"报错，只需成功执行一次。
-- ============================================================

USE practice2;

ALTER TABLE `user`
    ADD COLUMN member_level TINYINT NOT NULL DEFAULT 0 COMMENT '会员等级 0-普通 1-白银 2-黄金 3-钻石' AFTER `sign`,
    ADD COLUMN member_expire DATETIME DEFAULT NULL COMMENT '会员到期时间（NULL 表示从未开通）' AFTER `member_level`,
    ADD COLUMN role TINYINT NOT NULL DEFAULT 0 COMMENT '角色 0-普通用户 1-管理员（可审核/下架他人视频）' AFTER `member_expire`;
