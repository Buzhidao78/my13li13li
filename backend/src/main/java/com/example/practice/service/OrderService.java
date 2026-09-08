package com.example.practice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.practice.dto.UpgradeMessage;
import com.example.practice.vo.MemberInfoVO;
import com.example.practice.vo.MemberOrderVO;

/**
 * 订单服务：会员充值订单的核心业务
 * 涉及 Redis（分布式锁/缓存/幂等）+ RabbitMQ（可靠投递/延时关单）两大练习点
 */
public interface OrderService {

    /**
     * 创建订单（充值下单）
     * Redis 练习点：分布式锁防重复下单 + 订单写缓存
     * MQ 练习点：发送 TTL 延时消息，用于到期自动关闭未支付订单
     */
    MemberOrderVO createOrder(Long userId, Integer level);

    /**
     * 模拟支付：把订单置为已支付，并发送"会员升级"MQ 消息（异步处理）
     * MQ 练习点：生产者可靠投递（confirm 确认）
     */
    MemberOrderVO payOrder(Long userId, String orderId, boolean simulateError);

    /**
     * 查询订单详情（优先读 Redis 缓存，减轻 MySQL 压力）
     */
    MemberOrderVO getOrder(Long userId, String orderId);

    /**
     * 分页查询当前用户的所有订单（按创建时间倒序）
     * 使用 MyBatis-Plus 的 IPage 分页，返回 IPage<MemberOrderVO>（含 records/total/current/pages）
     */
    IPage<MemberOrderVO> listMyOrders(Long userId, long page, long size);

    /**
     * 查询当前用户最新的"待支付"订单（没有则返回 null）
     * 用于页面顶部展示"继续支付"入口：无论订单列表翻到第几页，待支付订单都能被找到
     */
    MemberOrderVO getPendingOrder(Long userId);

    /**
     * 处理"会员升级"消息（由 MQ 消费者调用）
     * Redis 练习点：MQ 消费幂等（已处理的 orderId 不再重复处理）
     */
    void upgradeMember(UpgradeMessage msg);

    /**
     * 关闭过期未支付订单（由死信队列消费者调用，延时任务）
     */
    void closeExpiredOrder(String orderId);

    /**
     * 查询当前用户的会员信息（等级、到期时间、剩余天数）
     */
    MemberInfoVO getMemberInfo(Long userId);

    /**
     * 补发"延时关单"消息（MQ 发送失败补偿任务用）
     * 内部会校验：订单仍存在且仍为待支付状态，才真正重发
     */
    void resendCloseMessage(String orderId);

    /**
     * 补发"会员升级"消息（MQ 发送失败补偿任务用）
     * 内部会校验：订单已支付 且 尚未升级成功（无幂等标记），才真正重发
     */
    void resendUpgradeMessage(String orderId);
}
