package com.example.practice.dto;

import lombok.Data;

/**
 * 发表评论请求参数
 * parentId = 0 表示一级评论；大于 0 表示楼中楼回复
 */
@Data
public class CommentDTO {

    /** 评论内容（必填，≤500字） */
    private String content;

    /** 被回复的评论 ID：0 或空 = 一级评论 */
    private Long parentId;
}
