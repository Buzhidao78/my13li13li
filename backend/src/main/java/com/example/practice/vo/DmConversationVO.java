package com.example.practice.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私信会话 VO：会话列表项（归属者视角）
 */
@Data
public class DmConversationVO {

    /** 对方用户ID */
    private Long peerId;

    /** 对方昵称 */
    private String peerNickname;

    /** 对方头像 */
    private String peerAvatar;

    /** 最后一条消息摘要（截断100字） */
    private String lastContent;

    /** 最后一条消息时间 */
    private LocalDateTime lastTime;

    /** 我未读的对方消息数（会话列表角标） */
    private Integer unreadCount;
}
