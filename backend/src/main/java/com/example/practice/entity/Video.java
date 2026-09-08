package com.example.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频实体：对应 video 表
 * 视频上传后落库，包含文件路径、状态、互动计数等字段
 */
@Data
@TableName("video")
public class Video {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上传用户 ID */
    private Long userId;

    /** 视频标题 */
    private String title;

    /** 视频简介 */
    private String description;

    /** 视频文件访问路径（如 /upload/20260907/xxx.mp4） */
    private String videoUrl;

    /** 封面访问路径（可选） */
    private String coverUrl;

    /** 分类：0默认 1生活 2游戏 3科技 4美食 */
    private Integer category;

    /** 状态：0待审核 1已发布 2已驳回 3已下架（见 constant.VideoStatus） */
    private Integer status;

    /** 播放量（模块 1 先落库，播放计数在后续模块用 Redis 做） */
    private Integer playCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 收藏数 */
    private Integer favoriteCount;

    /** 投币数 */
    private Integer coinCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
