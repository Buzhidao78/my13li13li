package com.example.practice.dto;

import lombok.Data;

/**
 * 发送私信入参 DTO
 */
@Data
public class DmSendDTO {

    /** 接收者用户ID */
    private Long receiverId;

    /** 消息内容（纯文本，1~500字，服务端会 trim） */
    private String content;
}
