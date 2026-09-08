package com.example.practice.dto;

import lombok.Data;

/**
 * MQ 消息体：支付成功后通知"会员升级"的消息内容
 * 生产者（支付接口）发这个对象，消费者（升级监听器）接收它
 */
@Data
public class UpgradeMessage {

    /** 订单号（唯一标识，幂等判断就用它） */
    private String orderId;

    /** 用户 id */
    private Long userId;

    /** 购买的会员等级 */
    private Integer level;

    /** 会员时长（天） */
    private Integer days;

    /**
     * 演示开关：为 true 时消费者故意抛异常，让消息重回队列
     * 仅用于教学演示"消费者报错 -> 消息重回队列"，正常业务传 false/null
     */
    private Boolean simulateError;
}
