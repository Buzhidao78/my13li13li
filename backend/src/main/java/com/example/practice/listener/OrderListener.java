package com.example.practice.listener;

import com.example.practice.constant.RabbitConstants;
import com.example.practice.dto.UpgradeMessage;
import com.example.practice.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 订单 MQ 消费者：两个监听器，分别演示不同的 MQ 可靠性场景
 *
 * 1. handleUpgrade：会员升级消费 —— 手动 ACK + Redis 幂等 + 异常重回队列
 * 2. handleClose：延时关单消费 —— 消费死信队列，关闭超时未支付的订单
 */
@Slf4j
@Component
public class OrderListener {

    /** 消费者重试上限：超过后放弃消息（避免无限重回队列） */
    private static final long MAX_RETRY = 3;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 会员升级消费者（监听 member.upgrade.queue）
     *
     * MQ 可靠性练习点：
     * - 手动 ACK：处理成功才 basicAck 确认；失败不确认，消息重回队列
     * - 业务幂等：Redis 存已处理的 orderId，重复投递时直接跳过（见 OrderServiceImpl.upgradeMember）
     * - 异常重回队列：simulateError=true 且首次消费时故意抛异常，观察消息被重新投递并最终成功
     */
    @RabbitListener(queues = RabbitConstants.Q_MEMBER_UPGRADE)
    public void handleUpgrade(UpgradeMessage msg,
                              @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                              Channel channel) throws Exception {
        String orderId = msg.getOrderId();
        try {
            // 演示开关：simulateError=true 且是第 1 次消费时，故意抛异常 -> 消息重回队列重试
            boolean firstAttempt = Boolean.FALSE.equals(stringRedisTemplate.hasKey(RabbitConstants.RETRY_PREFIX + orderId));
            if (Boolean.TRUE.equals(msg.getSimulateError()) && firstAttempt) {
                throw new RuntimeException("模拟消费者处理异常（第 1 次），消息将重回队列重试");
            }

            // 真正的业务处理（内部有 Redis 幂等判断，重复消息直接跳过）
            orderService.upgradeMember(msg);

            // 处理成功 -> 手动 ACK（告诉 RabbitMQ 这条消息处理完了，可以从队列删除）
            channel.basicAck(deliveryTag, false);
            // 清理重试计数，为下一次可能的重试清零
            stringRedisTemplate.delete(RabbitConstants.RETRY_PREFIX + orderId);
            log.info("会员升级消息已成功消费并确认, orderId={}", orderId);
        } catch (Exception e) {
            // 处理失败：统计重试次数
            Long retry = stringRedisTemplate.opsForValue().increment(RabbitConstants.RETRY_PREFIX + orderId);
            if (retry <= MAX_RETRY) {
                // 未超过上限 -> 重回队列（requeue=true），RabbitMQ 会再次投递给消费者
                log.error("消费者处理失败（第{}次），消息重回队列, orderId={}, 原因={}", retry, orderId, e.getMessage());
                channel.basicNack(deliveryTag, false, true);
            } else {
                // 超过上限 -> 确认丢弃（防止消息死循环）；生产环境一般会转入人工处理/专门的死信
                log.error("重试超过{}次上限，放弃该消息, orderId={}", MAX_RETRY, orderId);
                stringRedisTemplate.delete(RabbitConstants.RETRY_PREFIX + orderId);
                channel.basicAck(deliveryTag, false);
            }
        }
    }

    /**
     * 延时关单消费者（监听 order.close.queue，由死信队列转投而来）
     *
     * 延时任务原理：下单时发一条带 TTL 的消息到 order.ttl.queue，
     * TTL 到期后 RabbitMQ 自动把它投递到死信交换机 -> 关单队列，
     * 这里收到后检查订单，若仍未支付则关闭。
     */
    @RabbitListener(queues = RabbitConstants.Q_ORDER_CLOSE)
    public void handleClose(String orderId,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                            Channel channel) throws Exception {
        try {
            orderService.closeExpiredOrder(orderId);
            // 处理成功 -> 手动 ACK
            channel.basicAck(deliveryTag, false);
            log.info("关单消息已消费并确认, orderId={}", orderId);
        } catch (Exception e) {
            // 关单失败重回队列重试（保证消息不丢）
            log.error("关单处理失败，消息重回队列, orderId={}, 原因={}", orderId, e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
