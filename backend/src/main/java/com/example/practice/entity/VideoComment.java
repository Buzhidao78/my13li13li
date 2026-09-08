package com.example.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频评论实体
 * 楼中楼结构：parent_id 指向被回复的评论，root_id 指向所属的一级评论
 */
@Data
@TableName("video_comment")
public class VideoComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属视频 */
    private Long videoId;

    /** 评论用户 */
    private Long userId;

    /** 评论内容 */
    private String content;

    /** 回复的目标评论ID：0 = 一级评论 */
    private Long parentId;

    /** 所属一级评论ID（一级评论的 root_id 就是自己） */
    private Long rootId;

    private LocalDateTime createTime;
}
