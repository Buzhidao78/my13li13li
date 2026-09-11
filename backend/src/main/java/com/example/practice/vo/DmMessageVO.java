package com.example.practice.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私信消息 VO：聊天窗口里的单条消息（历史消息与 WebSocket 实时推送共用）
 */
@Data
public class DmMessageVO {

    /** 消息ID（前端用于双通道去重：HTTP 历史 vs WS 推送） */
    private Long id;

    /** 发送者用户ID */
    private Long senderId;

    /** 接收者用户ID */
    private Long receiverId;

    /** 消息内容（纯文本，前端文本插值渲染防XSS） */
    private String content;

    /** 发送时间 */
    private LocalDateTime createTime;
}
