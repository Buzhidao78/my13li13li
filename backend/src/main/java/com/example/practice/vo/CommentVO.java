package com.example.practice.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论出参 VO：一级评论带 replies（楼中楼回复列表）
 */
@Data
public class CommentVO {

    private Long id;

    private Long videoId;

    /** 评论用户 ID */
    private Long userId;

    /** 评论者昵称 */
    private String nickname;

    /** 评论内容 */
    private String content;

    /** 回复的目标评论 ID（0 = 一级评论） */
    private Long parentId;

    /** 所属一级评论 ID */
    private Long rootId;

    private LocalDateTime createTime;

    /** 楼中楼回复列表（一级评论才有，回复本身无此字段） */
    private List<CommentVO> replies;
}
