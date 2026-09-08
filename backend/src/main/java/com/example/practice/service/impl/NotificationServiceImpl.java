package com.example.practice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.practice.constant.NotificationType;
import com.example.practice.entity.User;
import com.example.practice.entity.UserNotification;
import com.example.practice.entity.Video;
import com.example.practice.mapper.UserMapper;
import com.example.practice.mapper.UserNotificationMapper;
import com.example.practice.mapper.VideoMapper;
import com.example.practice.service.NotificationService;
import com.example.practice.vo.NotificationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 站内通知服务实现
 * 职责：
 * 1. 提供 notify() 给互动触发点（点赞/收藏/投币/评论/回复/关注）调用，负责拼文案 + 落库
 * 2. 提供通知中心的查询/已读接口
 * 文案规则：昵称 + 动作 + 视频名（+ 评论内容片段）
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    /** 评论内容附在通知里的最大长度（超出截断，避免通知列表被长评论刷屏） */
    private static final int EXTRA_MAX_LEN = 50;

    @Autowired
    private UserNotificationMapper notificationMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private VideoMapper videoMapper;

    @Override
    public IPage<NotificationVO> list(Long userId, long page, long size) {
        Page<UserNotification> p = notificationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<UserNotification>()
                        .eq(UserNotification::getUserId, userId)
                        .orderByDesc(UserNotification::getCreateTime));
        return p.convert(this::toVO);
    }

    @Override
    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsRead, 0));
    }

    @Override
    public void readAll(Long userId) {
        // 只更新"未读"的记录为已读，避免无谓的写操作
        notificationMapper.update(null, new LambdaUpdateWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsRead, 0)
                .set(UserNotification::getIsRead, 1));
    }

    @Override
    public void readOne(Long userId, Long id) {
        // 归属校验：where user_id = 当前用户，防止把别人的通知标已读
        notificationMapper.update(null, new LambdaUpdateWrapper<UserNotification>()
                .eq(UserNotification::getId, id)
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsRead, 0)
                .set(UserNotification::getIsRead, 1));
    }

    @Override
    public void notify(Long userId, Long actorId, int type, Long videoId, String extra) {
        // 自己触发自己的行为不通知自己（给自己点赞、评论自己的视频都不打扰）
        if (userId == null || userId.equals(actorId)) {
            return;
        }
        User actor = userMapper.selectById(actorId);
        String nickname = actor == null ? "匿名用户" : actor.getNickname();
        Video video = videoId == null ? null : videoMapper.selectById(videoId);
        String title = video == null ? "" : video.getTitle();

        UserNotification n = new UserNotification();
        n.setUserId(userId);
        n.setActorId(actorId);
        n.setType(type);
        n.setVideoId(videoId);
        n.setContent(buildContent(nickname, type, title, extra));
        n.setIsRead(0);
        notificationMapper.insert(n);
    }

    /** 按类型拼通知文案：昵称 + 动作 + 视频名（+ 评论内容片段） */
    private String buildContent(String nickname, int type, String title, String extra) {
        String suffix = extra == null ? "" : "：" + truncate(extra);
        return switch (type) {
            case NotificationType.LIKE -> nickname + " 赞了你的视频《" + title + "》";
            case NotificationType.FAVORITE -> nickname + " 收藏了你的视频《" + title + "》";
            case NotificationType.COIN -> nickname + " 给你投了币：《" + title + "》";
            case NotificationType.COMMENT -> nickname + " 评论了你的视频《" + title + "》" + suffix;
            case NotificationType.REPLY -> nickname + " 回复了你的评论" + suffix;
            case NotificationType.FOLLOW -> nickname + " 关注了你";
            default -> nickname + " 和你互动了";
        };
    }

    /** 截断过长的附加文本 */
    private String truncate(String text) {
        return text.length() > EXTRA_MAX_LEN ? text.substring(0, EXTRA_MAX_LEN) + "…" : text;
    }

    /** 通知实体 → VO：联查触发者昵称和视频标题 */
    private NotificationVO toVO(UserNotification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setType(n.getType());
        vo.setActorId(n.getActorId());
        vo.setVideoId(n.getVideoId());
        vo.setContent(n.getContent());
        vo.setIsRead(n.getIsRead());
        vo.setCreateTime(n.getCreateTime());
        // 联查触发者昵称（练习场景直接单查，数据量小可接受）
        User actor = n.getActorId() == null ? null : userMapper.selectById(n.getActorId());
        vo.setActorNickname(actor == null ? "匿名用户" : actor.getNickname());
        // 联查视频标题（关注通知没有关联视频）
        if (n.getVideoId() != null) {
            Video video = videoMapper.selectById(n.getVideoId());
            vo.setVideoTitle(video == null ? "" : video.getTitle());
        }
        return vo;
    }
}
