package com.example.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私信会话实体：对应 dm_conversation 表
 * 设计要点：每个用户对每个对方存"一行自己的会话"——
 * 会话归属者(user_id)视角记录与对方(peer_id)的最后一条摘要和自己的未读数。
 * 这样"我的会话列表"只需单表按 update_time 倒序一查，"未读总数"一条 SUM 即可，
 * 无需 join/聚合；代价是发一条消息要同时维护发送方/接收方两行（写放大），练习项目可接受。
 */
@Data
@TableName("dm_conversation")
public class DmConversation {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话归属者（这一行是"谁的"会话） */
    private Long userId;

    /** 对方用户ID */
    private Long peerId;

    /** 最后一条消息ID（冗余，方便排查） */
    private Long lastMessageId;

    /** 最后一条消息摘要（截断100字，会话列表展示用） */
    private String lastContent;

    /** 最后一条消息时间 */
    private LocalDateTime lastTime;

    /** 我未读的对方消息数（红点角标） */
    private Integer unreadCount;

    /** 会话创建时间 */
    private LocalDateTime createTime;

    /** 最后活跃时间（会话列表排序用，发消息时更新） */
    private LocalDateTime updateTime;
}
