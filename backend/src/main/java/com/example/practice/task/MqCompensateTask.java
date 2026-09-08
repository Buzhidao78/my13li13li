package com.example.practice.task;

import com.example.practice.constant.RabbitConstants;
import com.example.practice.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * MQ 发送失败补偿定时任务
 * 场景：生产者 confirm 失败 / return 路由失败 / convertAndSend 抛异常时，
 * RabbitMQConfig 与 OrderServiceImpl 会把 {类型}|{orderId} 写入 Redis 失败补偿队列（mq:fail:list）。
 * 本任务每 30 秒从队列取出并重发，保证"下单 -> 延时关单"、"支付 -> 会员升级"两条链路消息最终不丢。
 *
 * 兜底设计：
 * - 重发内部有幂等校验（订单状态/幂等标记），重复补偿不会产生副作用；
 * - 处理抛异常的条目会被放回队尾稍后重试，不会丢失也不会阻塞后续条目。
 */
@Slf4j
@Component
public class MqCompensateTask {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private OrderService orderService;

    /** 单次最多处理条数：防止 Redis 中堆积大量失败消息时任务长时间占用 */
    private static final int MAX_PER_RUN = 100;

    /** 每 30 秒扫一次失败队列 */
    @Scheduled(fixedDelay = 30_000)
    public void compensate() {
        for (int i = 0; i < MAX_PER_RUN; i++) {
            // rightPop：从队尾取出（入队用 leftPush，先失败的先被取走）
            String item = stringRedisTemplate.opsForList().rightPop(RabbitConstants.FAIL_LIST_KEY);
            if (item == null) {
                break;
            }
            int idx = item.indexOf('|');
            if (idx <= 0 || idx == item.length() - 1) {
                log.warn("MQ 补偿队列存在非法条目，已丢弃: {}", item);
                continue;
            }
            String type = item.substring(0, idx);
            String orderId = item.substring(idx + 1);
            try {
                if (RabbitConstants.FAIL_TYPE_CLOSE.equals(type)) {
                    orderService.resendCloseMessage(orderId);
                } else if (RabbitConstants.FAIL_TYPE_UPGRADE.equals(type)) {
                    orderService.resendUpgradeMessage(orderId);
                } else {
                    log.warn("MQ 补偿队列存在未知类型，已丢弃: {}", item);
                }
            } catch (Exception e) {
                // 重发/查询抛异常：放回队尾，等下一轮再试（避免毒丸消息打断整个任务）
                log.error("MQ 补偿处理异常，放回队尾稍后重试, item={}, 原因={}", item, e.getMessage());
                try {
                    stringRedisTemplate.opsForList().leftPush(RabbitConstants.FAIL_LIST_KEY, item);
                } catch (Exception redisEx) {
                    log.error("MQ 补偿条目回写 Redis 失败: {}", item, redisEx);
                }
            }
        }
    }
}
