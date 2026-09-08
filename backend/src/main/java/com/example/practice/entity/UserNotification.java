package com.example.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知实体：对应 user_notification 表
 * 互动行为（点赞/收藏/投币/评论/回复/关注）发生时，给被影响的人写一条通知
 */
@Data
@TableName("user_notification")
public class UserNotification {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收者（被通知的人） */
    private Long userId;

    /** 触发者（谁点的赞/评论的） */
    private Long actorId;

    /** 通知类型：1点赞 2收藏 3投币 4评论 5回复 6关注（见 constant.NotificationType） */
    private Integer type;

    /** 关联视频 ID（关注通知为空） */
    private Long videoId;

    /** 通知摘要文本（如"用户0004 赞了你的视频《xxx》"） */
    private String content;

    /** 是否已读：0未读 1已读 */
    private Integer isRead;

    /** 通知时间（数据库默认当前时间） */
    private LocalDateTime createTime;
}
