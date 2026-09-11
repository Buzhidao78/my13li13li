package com.example.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私信消息实体：对应 dm_message 表
 * 只存发送方/接收方/内容/时间，不存"已读"状态——
 * 已读是"会话级"的概念（进入会话全部已读），由 dm_conversation.unread_count 承载。
 * 抖音规则判定也基于这张表：非互关时"pair 里最后一条消息是否是我发的"。
 */
@Data
@TableName("dm_message")
public class DmMessage {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发送者 */
    private Long senderId;

    /** 接收者 */
    private Long receiverId;

    /** 消息内容（纯文本，≤500字；前端用文本插值渲染防XSS） */
    private String content;

    /** 发送时间（数据库默认当前时间） */
    private LocalDateTime createTime;
}
