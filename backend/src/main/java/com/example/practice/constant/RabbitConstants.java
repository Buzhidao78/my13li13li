package com.example.practice.constant;

/**
 * RabbitMQ 相关常量：交换机名、队列名、路由键统一放这里，避免到处写字符串拼错
 */
public class RabbitConstants {

    // ============ 会员升级（Direct 直连交换机，演示可靠投递） ============
    /** 会员业务交换机（Direct 类型） */
    public static final String EX_MEMBER = "member.exchange";
    /** 会员升级队列 */
    public static final String Q_MEMBER_UPGRADE = "member.upgrade.queue";
    /** 会员升级路由键：支付成功 -> 异步升级会员 */
    public static final String RK_MEMBER_UPGRADE = "member.upgrade";

    // ============ 延时关单（TTL + 死信交换机 DLX，演示延时任务） ============
    /** 订单 TTL 交换机：下单后发一条带过期时间的消息 */
    public static final String EX_ORDER_TTL = "order.ttl.exchange";
    /** 订单 TTL 队列：消息在此等待 TTL 到期 */
    public static final String Q_ORDER_TTL = "order.ttl.queue";
    /** 订单创建路由键 */
    public static final String RK_ORDER_CREATE = "order.create";

    /** 订单死信交换机（DLX）：TTL 到期的消息会被投递到这里 */
    public static final String EX_ORDER_DLX = "order.dlx.exchange";
    /** 关单队列：真正执行"关闭未支付订单"的消费者监听它 */
    public static final String Q_ORDER_CLOSE = "order.close.queue";
    /** 关单路由键（死信重投时使用的路由键） */
    public static final String RK_ORDER_CLOSE = "order.close";

    // ============ Redis key 统一前缀（防冲突、易排查） ============
    /** 分布式锁：下单防重复。key = order:lock:{userId} */
    public static final String LOCK_ORDER_PREFIX = "order:lock:";
    /** 订单缓存。key = order:{orderId}，存订单 JSON，减轻 MySQL 压力 */
    public static final String CACHE_ORDER_PREFIX = "order:";
    /** MQ 消费幂等标记。key = processed:{orderId}，表示该订单已处理过 */
    public static final String IDEMPOTENT_PREFIX = "processed:";
    /** 消费者重试次数统计。key = retry:{orderId}，达到上限后不再重回队列 */
    public static final String RETRY_PREFIX = "retry:";

    // ============ 生产端可靠投递补偿 ============
    /**
     * MQ 发送失败补偿队列（Redis List）：
     * confirm 失败 / 路由失败 / 发送抛异常时，把 {类型}|{orderId} 写入该队列，
     * 由 MqCompensateTask 定时扫描重发，保证"订单创建/支付 -> 关单/升级消息"不丢。
     * 元素格式：CLOSE|orderId 或 UPGRADE|orderId
     */
    public static final String FAIL_LIST_KEY = "mq:fail:list";

    /** 补偿消息类型：延时关单（订单创建后发送失败/未确认） */
    public static final String FAIL_TYPE_CLOSE = "CLOSE";

    /** 补偿消息类型：会员升级（支付后发送失败/未确认） */
    public static final String FAIL_TYPE_UPGRADE = "UPGRADE";

    // ============ 视频封面异步抽帧（Direct 直连交换机，演示异步任务队列） ============
    /** 视频业务交换机（Direct 类型） */
    public static final String EX_VIDEO = "video.exchange";
    /** 封面抽帧队列：上传未带封面时，异步调用 FFmpeg 抽帧，避免阻塞上传接口 */
    public static final String Q_COVER_EXTRACT = "video.cover.extract.queue";
    /** 封面抽帧路由键 */
    public static final String RK_COVER_EXTRACT = "video.cover.extract";
}
