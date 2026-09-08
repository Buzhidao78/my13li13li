package com.example.practice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.practice.vo.NotificationVO;

/**
 * 站内通知服务：通知的生成（互动触发点调用）与查询（通知中心）
 */
public interface NotificationService {

    /**
     * 分页查询我的通知（按通知时间倒序）
     */
    IPage<NotificationVO> list(Long userId, long page, long size);

    /**
     * 未读通知数（导航栏红点用）
     */
    long unreadCount(Long userId);

    /**
     * 全部标记已读
     */
    void readAll(Long userId);

    /**
     * 单条标记已读（校验归属：只能读自己的通知）
     */
    void readOne(Long userId, Long id);

    /**
     * 创建一条通知（互动行为触发时调用）
     * @param userId  接收者（被通知的人）
     * @param actorId 触发者（谁点的赞）
     * @param type    通知类型（见 constant.NotificationType）
     * @param videoId 关联视频 ID（关注通知传 null）
     * @param extra   附加文本（评论内容/回复内容，其他类型传 null）
     * 规则：自己触发自己的行为不通知自己（接收者 == 触发者 时静默跳过）
     */
    void notify(Long userId, Long actorId, int type, Long videoId, String extra);
}
