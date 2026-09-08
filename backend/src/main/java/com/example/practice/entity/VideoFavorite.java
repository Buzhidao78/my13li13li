package com.example.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频收藏实体：一张"用户-视频"关系表
 * 数据库 unique 约束保证同一用户不能重复收藏
 */
@Data
@TableName("video_favorite")
public class VideoFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收藏的视频 */
    private Long videoId;

    /** 收藏的用户 */
    private Long userId;

    private LocalDateTime createTime;
}
