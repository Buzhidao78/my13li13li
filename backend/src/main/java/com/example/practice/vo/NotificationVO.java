package com.example.practice.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知出参 VO：通知列表展示用
 * 在实体基础上补充触发者昵称、关联视频标题，方便前端直接渲染（避免再发请求）
 */
@Data
public class NotificationVO {

    private Long id;

    /** 通知类型：1点赞 2收藏 3投币 4评论 5回复 6关注 */
    private Integer type;

    /** 触发者 ID */
    private Long actorId;

    /** 触发者昵称（联查 user 表填充） */
    private String actorNickname;

    /** 关联视频 ID（关注通知为空） */
    private Long videoId;

    /** 关联视频标题（联查 video 表填充） */
    private String videoTitle;

    /** 通知摘要文本（服务端已拼好，前端直接展示） */
    private String content;

    /** 是否已读：0未读 1已读 */
    private Integer isRead;

    /** 通知时间 */
    private LocalDateTime createTime;
}
