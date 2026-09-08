-- ============================================================
-- 视频社区测试数据脚本（可重复执行：先清空再重建）
-- 依赖：请先执行 src/main/resources/db/schema.sql 建好全部表
-- 执行方式：mysql -uroot -p123456 < test-data.sql
-- ============================================================

USE practice2;

-- 1. 清空业务数据（表结构已由 schema.sql 建好，这里只清数据）
TRUNCATE TABLE video_comment;
TRUNCATE TABLE video_favorite;
TRUNCATE TABLE watch_history;
TRUNCATE TABLE video_coin;
TRUNCATE TABLE user_follow;
TRUNCATE TABLE user_notification;
TRUNCATE TABLE video;

-- 2. 测试用户（作者们；登录测试统一用手机号验证码，密码留空）
--    INSERT IGNORE：用户已存在（重复执行脚本）时跳过，不报错
--    role=1 的管理员（账号 admin / 密码 123456，用"账号密码登录"）
INSERT IGNORE INTO user (username, password, phone, nickname, gender, status, role) VALUES
('admin', '$2a$10$MuL39fiFGAI.Zp44Lhw2neSdqG6VG9oBtQsB7E.F3kBfLZYtCbe16', '13800000001', '管理员', 0, 0, 1);

INSERT IGNORE INTO user (phone, nickname, gender, status, role) VALUES
('13800000003', '用户0003', 0, 0, 0),
('13800000004', '用户0004', 0, 0, 0),
('13800000005', '用户0005', 0, 0, 0);

-- 3. 规范测试视频（12 条）
--    作者分布：13800000002(用户0002,id=9)、用户0003、用户0004、用户0005
--    状态分布：9 条已发布(1)、2 条待审核(0)、1 条已驳回(2)
--    计数分布：热门/普通/新发布区分明显，方便测试推荐排序
--    video_url 复用模块1上传的真实文件（假视频，播放器不可播但 URL 有效）
SET @v9 = 9;
SET @u3 = (SELECT id FROM user WHERE phone = '13800000003');
SET @u4 = (SELECT id FROM user WHERE phone = '13800000004');
SET @u5 = (SELECT id FROM user WHERE phone = '13800000005');

INSERT INTO video (user_id, title, description, video_url, cover_url, category, status, play_count, like_count, favorite_count, coin_count, create_time) VALUES
(@v9, '我的第一个视频', '模块1上传的测试视频，作为规范数据保留。', '/upload/20260907/8818410d19984a56bda7b3cd48488db7.mp4', NULL, 1, 1, 233, 45, 12, 8, NOW() - INTERVAL 3 DAY),
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

-- 4. 评论数据（含楼中楼：root_id 指向一级评论）
INSERT INTO video_comment (video_id, user_id, content, parent_id, root_id, create_time) VALUES
-- 视频2（红烧肉）的讨论
(2, @u4, '这个教程太详细了，跟着做成功了！', 0, 0, NOW() - INTERVAL 20 HOUR),
(2, @v9, 'up主请问酱油是生抽还是老抽？', 0, 0, NOW() - INTERVAL 18 HOUR),
(2, @u3, '回楼上：用的是生抽提鲜，老抽上色，教程里第3分钟有讲', 2, 2, NOW() - INTERVAL 17 HOUR),
(2, @u5, '收藏了，周末试试', 0, 0, NOW() - INTERVAL 12 HOUR),
-- 视频3（黑神话）的讨论
(3, @v9, '这个BOSS打得太丝滑了', 0, 0, NOW() - INTERVAL 4 HOUR),
(3, @u5, '求个配装，我卡在这一关了', 0, 0, NOW() - INTERVAL 3 HOUR);

-- 5. 收藏数据
INSERT INTO video_favorite (video_id, user_id, create_time) VALUES
(3, @v9, NOW() - INTERVAL 3 HOUR),
(5, @v9, NOW() - INTERVAL 2 HOUR),
(2, @u3, NOW() - INTERVAL 1 DAY),
(8, @u3, NOW() - INTERVAL 20 HOUR);

-- 6. 观看历史
INSERT INTO watch_history (video_id, user_id, watch_time) VALUES
(2, @v9, NOW() - INTERVAL 18 HOUR),
(3, @v9, NOW() - INTERVAL 4 HOUR),
(4, @v9, NOW() - INTERVAL 1 DAY),
(8, @v9, NOW() - INTERVAL 2 DAY),
(2, @u3, NOW() - INTERVAL 10 HOUR);

-- 7. 投币记录
INSERT INTO video_coin (video_id, user_id, coin, create_time) VALUES
(2, @v9, 1, NOW() - INTERVAL 18 HOUR),
(8, @v9, 2, NOW() - INTERVAL 2 DAY),
(3, @u5, 1, NOW() - INTERVAL 3 HOUR);

-- 8. 关注关系：用户0002(id=9) 与 0003/0004/0005 互相关注，方便测试动态流和粉丝列表
INSERT INTO user_follow (follower_id, following_id, create_time) VALUES
(@v9, @u3, NOW() - INTERVAL 3 DAY),
(@v9, @u4, NOW() - INTERVAL 2 DAY),
(@u3, @v9, NOW() - INTERVAL 1 DAY),
(@u4, @v9, NOW() - INTERVAL 20 HOUR),
(@u5, @v9, NOW() - INTERVAL 10 HOUR),
(@u3, @u4, NOW() - INTERVAL 5 HOUR);

-- 9. 站内通知：给用户0002(id=9) 造各类型通知（video_id=1 是他的视频《我的第一个视频》）
INSERT INTO user_notification (user_id, actor_id, type, video_id, content, is_read, create_time) VALUES
(@v9, @u3, 1, 1, '用户0003 赞了你的视频《我的第一个视频》', 0, NOW() - INTERVAL 5 HOUR),
(@v9, @u4, 4, 1, '用户0004 评论了你的视频《我的第一个视频》：哈哈，学到了', 0, NOW() - INTERVAL 4 HOUR),
(@v9, @u5, 2, 1, '用户0005 收藏了你的视频《我的第一个视频》', 1, NOW() - INTERVAL 3 HOUR),
(@v9, @u4, 3, 1, '用户0004 给你投了币：《我的第一个视频》', 0, NOW() - INTERVAL 2 HOUR),
(@v9, @u5, 6, NULL, '用户0005 关注了你', 0, NOW() - INTERVAL 1 HOUR);
