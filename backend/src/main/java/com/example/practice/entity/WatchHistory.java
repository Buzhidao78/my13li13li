package com.example.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 观看历史实体：唯一约束保证同一用户对同一视频只留一条，重复观看只更新时间
 */
@Data
@TableName("watch_history")
public class WatchHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 看过的视频 */
    private Long videoId;

    /** 观看用户 */
    private Long userId;

    /** 最近观看时间 */
    private LocalDateTime watchTime;
}
