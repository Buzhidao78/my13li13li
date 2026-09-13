package com.example.practice.config;

import com.example.practice.constant.RabbitConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置类：负责把交换机、队列、绑定关系一次性声明好
 *
 * 练习要点：
 * 1. Direct 交换机：按路由键精确匹配路由
 * 2. 队列持久化（durable=true）：MQ 重启后队列不丢
 * 3. 死信交换机 DLX：TTL 到期 / 消息被拒绝时，转投到死信队列
 * 4. 生产者 confirm + return：确认消息是否真正到达交换机/队列
 * 5. 失败补偿：confirm 失败 / return 路由失败的消息写入 Redis 失败队列，
 *    由 MqCompensateTask 定时重发（生产端可靠投递的兜底）
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // ============ 消息转换器：Java 对象 <-> JSON 字符串 ============

    /**
     * 消息转换器：生产者发对象自动转 JSON，消费者收 JSON 自动转对象
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 改造 RabbitTemplate：接入消息转换器 + confirm 确认 + return 回退回调
     * confirm：消息是否成功投递到交换机（可靠投递第 1 道保险）
     * return：消息是否成功路由到队列（可靠投递第 2 道保险）
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        // mandatory=true：消息路由不到任何队列时，触发 return 回调，而不是静默丢弃
        template.setMandatory(true);

        // confirm 回调：每条消息投递后都会回调，ack=true 表示交换机已收到
        template.setConfirmCallback((correlationData, ack, cause) -> {
            String id = correlationData != null ? correlationData.getId() : null;
            if (ack) {
                log.info("MQ confirm 成功：消息已投递到交换机, id={}", id);
            } else {
                // 确认失败（如交换机不存在 / 连接异常）：记入失败补偿队列，等待定时任务重发
                log.error("MQ confirm 失败：消息未投递到交换机, id={}, 原因={}，已记入补偿队列", id, cause);
                if (id != null) {
                    enqueueByMessageId(id);
                }
            }
        });

        // return 回调：路由失败（交换机没找到匹配的队列）时回调，可在此做补偿/记录
        template.setReturnsCallback(returned -> {
            String body = new String(returned.getMessage().getBody());
            log.error("MQ return 路由失败：exchange={}, routingKey={}, 消息内容={}，已记入补偿队列",
                    returned.getExchange(), returned.getRoutingKey(), body);
            enqueueByMessageBody(body);
        });
        return template;
    }

    /**
     * 按消息 id 记入失败补偿队列：id 形如 CLOSE:{orderId} / UPGRADE:{orderId}
     */
    private void enqueueByMessageId(String id) {
        String type = null;
        String orderId = null;
        if (id.startsWith(RabbitConstants.FAIL_TYPE_CLOSE + ":")) {
            type = RabbitConstants.FAIL_TYPE_CLOSE;
            orderId = id.substring((RabbitConstants.FAIL_TYPE_CLOSE + ":").length());
        } else if (id.startsWith(RabbitConstants.FAIL_TYPE_UPGRADE + ":")) {
            type = RabbitConstants.FAIL_TYPE_UPGRADE;
            orderId = id.substring((RabbitConstants.FAIL_TYPE_UPGRADE + ":").length());
        }
        if (type != null && orderId != null) {
            recordFail(type, orderId);
        }
    }

    /**
     * 按消息体记入失败补偿队列（return 回调没有 correlation id，只能从 body 反推）：
     * 关单消息体 = 订单号 JSON 字符串（"xxx"）；升级消息体 = UpgradeMessage JSON（{"orderId":...}）
     */
    private void enqueueByMessageBody(String body) {
        try {
            if (body.startsWith("\"")) {
                // 关单消息：JSON 字符串形式的订单号
                recordFail(RabbitConstants.FAIL_TYPE_CLOSE, objectMapper.readValue(body, String.class));
            } else if (body.startsWith("{")) {
                // 升级消息：反序列化取 orderId
                recordFail(RabbitConstants.FAIL_TYPE_UPGRADE,
                        objectMapper.readTree(body).path("orderId").asText());
            }
        } catch (Exception e) {
            log.warn("MQ 失败消息解析失败，无法自动补偿：{}", body);
        }
    }

    /** 写入 Redis 失败补偿队列（左进右出，定时任务从右侧取出重发） */
    private void recordFail(String type, String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            return;
        }
        try {
            stringRedisTemplate.opsForList().leftPush(RabbitConstants.FAIL_LIST_KEY, type + "|" + orderId);
            log.info("已写入 MQ 失败补偿队列: {}|{}", type, orderId);
        } catch (Exception e) {
            // Redis 也不可用时只能靠日志兜底（该场景下订单状态本身可能也不可靠）
            log.error("MQ 失败补偿写入 Redis 失败: {}|{}", type, orderId, e);
        }
    }

    // ============ 会员升级：Direct 交换机 + 队列 + 绑定 ============

    /** 会员交换机（Direct，持久化：true） */
    @Bean
    public DirectExchange memberExchange() {
        return new DirectExchange(RabbitConstants.EX_MEMBER, true, false);
    }

    /** 会员升级队列（durable=true：队列持久化，MQ 重启不丢） */
    @Bean
    public Queue memberUpgradeQueue() {
        return new Queue(RabbitConstants.Q_MEMBER_UPGRADE, true);
    }

    /** 绑定：路由键 member.upgrade 精确路由到升级队列 */
    @Bean
    public Binding memberUpgradeBinding() {
        return BindingBuilder.bind(memberUpgradeQueue()).to(memberExchange()).with(RabbitConstants.RK_MEMBER_UPGRADE);
    }

    // ============ 延时关单：TTL 队列 + 死信交换机 DLX ============

    /** 订单 TTL 交换机（Direct）：接收"下单延时消息" */
    @Bean
    public DirectExchange orderTtlExchange() {
        return new DirectExchange(RabbitConstants.EX_ORDER_TTL, true, false);
    }

    /**
     * 订单 TTL 队列：消息在这里"躺"到 TTL 到期
     * 关键配置（死信机制）：
     * - x-dead-letter-exchange：TTL 到期后转投到哪个死信交换机
     * - x-dead-letter-routing-key：转投时使用的路由键
     * 这样"延时关闭订单"就变成了：下单发消息 -> 躺 TTL 秒 -> 自动进死信队列 -> 消费者关单
     */
    @Bean
    public Queue orderTtlQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", RabbitConstants.EX_ORDER_DLX);
        args.put("x-dead-letter-routing-key", RabbitConstants.RK_ORDER_CLOSE);
        return new Queue(RabbitConstants.Q_ORDER_TTL, true, false, false, args);
    }

    /** 绑定：路由键 order.create -> TTL 队列 */
    @Bean
    public Binding orderTtlBinding() {
        return BindingBuilder.bind(orderTtlQueue()).to(orderTtlExchange()).with(RabbitConstants.RK_ORDER_CREATE);
    }

    /** 订单死信交换机（DLX）：接收 TTL 到期 / 被拒绝的消息 */
    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange(RabbitConstants.EX_ORDER_DLX, true, false);
    }

    /** 关单队列：负责真正关闭未支付订单的消费者监听它 */
    @Bean
    public Queue orderCloseQueue() {
        return new Queue(RabbitConstants.Q_ORDER_CLOSE, true);
    }

    // ============ 视频封面异步抽帧：Direct 直连交换机 ============

    /** 视频交换机（Direct，持久化） */
    @Bean
    public DirectExchange videoExchange() {
        return new DirectExchange(RabbitConstants.EX_VIDEO, true, false);
    }

    /** 封面抽帧队列（durable=true：队列持久化，MQ 重启不丢） */
    @Bean
    public Queue coverExtractQueue() {
        return new Queue(RabbitConstants.Q_COVER_EXTRACT, true);
    }

    /** 绑定：路由键 video.cover.extract 精确路由到抽帧队列 */
    @Bean
    public Binding coverExtractBinding() {
        return BindingBuilder.bind(coverExtractQueue()).to(videoExchange()).with(RabbitConstants.RK_COVER_EXTRACT);
    }

    /** 绑定：死信交换机按 order.close 路由到关单队列 */
    @Bean
    public Binding orderCloseBinding() {
        return BindingBuilder.bind(orderCloseQueue()).to(orderDlxExchange()).with(RabbitConstants.RK_ORDER_CLOSE);
    }
}
