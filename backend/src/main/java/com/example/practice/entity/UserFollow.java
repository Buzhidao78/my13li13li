package com.example.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户关注关系实体：对应 user_follow 表
 * 一张"用户-用户"关系表：follower_id 关注者，following_id 被关注者（UP主）
 * 数据库 unique 约束保证同一用户不能重复关注同一个人
 */
@Data
@TableName("user_follow")
public class UserFollow {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关注者（操作方） */
    private Long followerId;

    /** 被关注者（UP主） */
    private Long followingId;

    /** 关注时间（数据库默认当前时间） */
    private LocalDateTime createTime;
}
