package com.example.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投币记录实体：每次投币落一条，用于校验"每人每天最多 3 枚"
 */
@Data
@TableName("video_coin")
public class VideoCoin {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被投币的视频 */
    private Long videoId;

    /** 投币用户 */
    private Long userId;

    /** 本次投币数量（1 或 2） */
    private Integer coin;

    private LocalDateTime createTime;
}
