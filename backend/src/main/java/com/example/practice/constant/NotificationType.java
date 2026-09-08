package com.example.practice.constant;

/**
 * 站内通知类型常量
 * 与 user_notification.type 字段对应，互动行为触发通知时用
 */
public class NotificationType {

    /** 点赞了你的视频 */
    public static final int LIKE = 1;

    /** 收藏了你的视频 */
    public static final int FAVORITE = 2;

    /** 给你投了币 */
    public static final int COIN = 3;

    /** 评论了你的视频 */
    public static final int COMMENT = 4;

    /** 回复了你的评论 */
    public static final int REPLY = 5;

    /** 关注了你 */
    public static final int FOLLOW = 6;

    private NotificationType() {
    }
}
