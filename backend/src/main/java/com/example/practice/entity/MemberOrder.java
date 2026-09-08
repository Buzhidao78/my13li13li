package com.example.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员充值订单实体：对应数据库表 member_order
 */
@Data
@TableName("member_order")
public class MemberOrder {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务订单号（全局唯一，Redis 幂等和 MQ 消息都以它为准） */
    private String orderId;

    /** 下单用户 id */
    private Long userId;

    /** 购买的会员等级：1-白银 2-黄金 3-钻石 */
    private Integer level;

    /** 订单金额（元） */
    private BigDecimal amount;

    /** 会员时长（天） */
    private Integer days;

    /** 订单状态：0-待支付 1-已支付 2-已关闭 3-已过期(延时关单) */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 关闭/过期时间 */
    private LocalDateTime closeTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
