package com.example.practice.vo;

import com.example.practice.constant.MemberLevel;
import com.example.practice.entity.MemberOrder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单视图对象：给前端展示订单信息（不含敏感字段）
 */
@Data
public class MemberOrderVO {

    /** 业务订单号 */
    private String orderId;

    /** 购买的会员等级 */
    private Integer level;

    /** 等级名称（由后端换算好，前端直接展示） */
    private String levelName;

    /** 订单金额（元） */
    private BigDecimal amount;

    /** 会员时长（天） */
    private Integer days;

    /** 订单状态：0-待支付 1-已支付 2-已关闭 3-已过期 */
    private Integer status;

    /** 状态描述（由后端换算好） */
    private String statusText;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 关闭/过期时间 */
    private LocalDateTime closeTime;

    /** 从实体转换 */
    public static MemberOrderVO from(MemberOrder order) {
        MemberOrderVO vo = new MemberOrderVO();
        vo.setOrderId(order.getOrderId());
        vo.setLevel(order.getLevel());
        MemberLevel.Info info = MemberLevel.of(order.getLevel());
        vo.setLevelName(info != null ? info.name : "未知");
        vo.setAmount(order.getAmount());
        vo.setDays(order.getDays());
        vo.setStatus(order.getStatus());
        vo.setStatusText(statusText(order.getStatus()));
        vo.setCreateTime(order.getCreateTime());
        vo.setPayTime(order.getPayTime());
        vo.setCloseTime(order.getCloseTime());
        return vo;
    }

    /** 订单状态 -> 中文描述 */
    public static String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "已支付";
            case 2 -> "已关闭";
            case 3 -> "已过期";
            default -> "未知";
        };
    }
}
