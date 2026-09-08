package com.example.practice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.practice.common.BusinessException;
import com.example.practice.constant.MemberLevel;
import com.example.practice.constant.RabbitConstants;
import com.example.practice.dto.UpgradeMessage;
import com.example.practice.entity.MemberOrder;
import com.example.practice.entity.User;
import com.example.practice.mapper.MemberOrderMapper;
import com.example.practice.mapper.UserMapper;
import com.example.practice.service.OrderService;
import com.example.practice.vo.MemberInfoVO;
import com.example.practice.vo.MemberOrderVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单服务实现：本练习的核心，聚合了 Redis + RabbitMQ 两大中间件的用法
 */
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private MemberOrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /** 未支付订单自动关闭的延时（毫秒），来自 application.yml 的 order.ttl-ms */
    @Value("${order.ttl-ms:30000}")
    private long ttlMs;

    // ==================== 下单（Redis 分布式锁 + 缓存 + TTL 延时消息） ====================

    @Override
    public MemberOrderVO createOrder(Long userId, Integer level) {
        // 1. 校验等级是否可购买（只能是 白银/黄金/钻石）
        if (level == null || !MemberLevel.isValid(level)) {
            throw new BusinessException("会员等级不合法");
        }

        // 2. Redis 分布式锁：防止同一个用户重复点击下单
        //    setIfAbsent 是原子操作：key 不存在才设置成功，返回 true 表示拿到锁
        //    锁带 5 秒过期，防止程序崩溃后锁永远不释放（死锁）
        String lockKey = RabbitConstants.LOCK_ORDER_PREFIX + userId;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(5));
        if (locked == null || !locked) {
            throw new BusinessException("正在处理中，请勿重复提交");
        }

        try {
            // 3. 双保险：查数据库是否已有待支付订单（防止锁过期后用户又点了一次）
            Long pendingCount = orderMapper.selectCount(new LambdaQueryWrapper<MemberOrder>()
                    .eq(MemberOrder::getUserId, userId)
                    .eq(MemberOrder::getStatus, 0));
            if (pendingCount != null && pendingCount > 0) {
                throw new BusinessException("您有待支付的订单，请先完成支付");
            }

            // 4. 组装订单并入库（状态 0=待支付）
            MemberLevel.Info info = MemberLevel.of(level);
            MemberOrder order = new MemberOrder();
            order.setOrderId(generateOrderId());
            order.setUserId(userId);
            order.setLevel(level);
            order.setAmount(info.amount);
            order.setDays(info.days);
            order.setStatus(0);
            orderMapper.insert(order);

            // 5. 订单信息写 Redis 缓存（读多写少，缓存能明显减轻 MySQL 压力）
            cacheOrder(order);

            // 6. 发送 TTL 延时消息：消息在 TTL 队列"躺" ttlMs 毫秒，
            //    到期后自动转投死信交换机 -> 关单队列 -> 消费者关闭未支付订单
            //    发送失败（MQ 暂不可用等）不阻塞下单：记入失败补偿队列，由 MqCompensateTask 稍后重发
            sendOrderCloseMessage(order.getOrderId());
            log.info("下单成功并已发送延时关单消息, orderId={}, TTL={}ms, userId={}", order.getOrderId(), ttlMs, userId);

            return MemberOrderVO.from(order);
        } finally {
            // 7. 释放分布式锁（无论成功失败都要释放）
            stringRedisTemplate.delete(lockKey);
        }
    }

    // ==================== 模拟支付（写库 + 发 MQ 可靠投递） ====================

    @Override
    public MemberOrderVO payOrder(Long userId, String orderId, boolean simulateError) {
        // 1. 直接查数据库（写操作以库为准，不走缓存）
        MemberOrder order = orderMapper.selectOne(new LambdaQueryWrapper<MemberOrder>()
                .eq(MemberOrder::getOrderId, orderId));
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该订单");
        }
        if (order.getStatus() != 0) {
            throw new BusinessException("订单状态不允许支付，请刷新后查看");
        }

        // 2. 更新订单为已支付：WHERE status=0 保证并发下只有一次成功（防重复支付）
        MemberOrder update = new MemberOrder();
        update.setStatus(1);
        update.setPayTime(LocalDateTime.now());
        int rows = orderMapper.update(update, new LambdaQueryWrapper<MemberOrder>()
                .eq(MemberOrder::getId, order.getId())
                .eq(MemberOrder::getStatus, 0));
        if (rows == 0) {
            throw new BusinessException("订单状态已变化，支付失败");
        }

        // 3. 刷新缓存中的订单状态
        order.setStatus(1);
        order.setPayTime(update.getPayTime());
        cacheOrder(order);

        // 4. 发送"会员升级"MQ 消息（异步：支付成功 -> 通知消费者升级会员）
        //    CorrelationData 的 id 带 UPGRADE: 前缀：confirm 失败时能据此自动补偿重发
        UpgradeMessage msg = new UpgradeMessage();
        msg.setOrderId(orderId);
        msg.setUserId(userId);
        msg.setLevel(order.getLevel());
        msg.setDays(order.getDays());
        msg.setSimulateError(simulateError); // 演示开关：为 true 时消费者故意报错重回队列
        sendMemberUpgradeMessage(msg);
        log.info("支付成功并已发送会员升级消息, orderId={}, userId={}, simulateError={}", orderId, userId, simulateError);

        return MemberOrderVO.from(order);
    }

    // ==================== 查询（优先缓存，减轻 MySQL 压力） ====================

    @Override
    public MemberOrderVO getOrder(Long userId, String orderId) {
        MemberOrder order = getByIdFromCacheOrDb(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权查看该订单");
        }
        return MemberOrderVO.from(order);
    }

    @Override
    public IPage<MemberOrderVO> listMyOrders(Long userId, long page, long size) {
        // 分页查询：MyBatis-Plus 自动拼接 LIMIT 并统计 total（需要分页插件支持）
        // Page 里 records 是当前页数据，total 是总条数，翻页按钮就靠它算总页数
        Page<MemberOrder> p = orderMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<MemberOrder>()
                        .eq(MemberOrder::getUserId, userId)
                        .orderByDesc(MemberOrder::getCreateTime));
        // 把实体列表转成 VO 列表（convert 会返回一个新的 Page，records 已转换）
        return p.convert(MemberOrderVO::from);
    }

    @Override
    public MemberOrderVO getPendingOrder(Long userId) {
        // 查最新一条"待支付"订单：不管订单列表翻到第几页，待支付订单始终能在这里被找到，
        // 保证页面顶部"继续支付"入口一直有效（避免分页后找不到待支付订单的死锁问题）
        MemberOrder order = orderMapper.selectOne(new LambdaQueryWrapper<MemberOrder>()
                .eq(MemberOrder::getUserId, userId)
                .eq(MemberOrder::getStatus, 0)
                .orderByDesc(MemberOrder::getCreateTime)
                .last("LIMIT 1"));
        return order == null ? null : MemberOrderVO.from(order);
    }

    // ==================== MQ 消费者调用的业务（幂等 + 升级） ====================

    @Override
    public void upgradeMember(UpgradeMessage msg) {
        String orderId = msg.getOrderId();

        // 1. MQ 消费幂等：Redis 里已存在"已处理"标记，说明这条消息处理过了，直接跳过
        //    避免因消息重复投递（重试/网络抖动）导致重复给用户加会员
        String idempotentKey = RabbitConstants.IDEMPOTENT_PREFIX + orderId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(idempotentKey))) {
            log.info("幂等命中：订单 {} 已处理过，跳过本次消费", orderId);
            return;
        }

        // 2. 校验订单确实已支付（防止消息被伪造或提前到达）
        MemberOrder order = orderMapper.selectOne(new LambdaQueryWrapper<MemberOrder>()
                .eq(MemberOrder::getOrderId, orderId));
        if (order == null || order.getStatus() != 1) {
            throw new BusinessException("订单未支付，不能升级会员");
        }

        // 3. 升级会员：等级取更高者（买黄金不会把钻石降级），时长在原来基础上叠加
        User user = userMapper.selectById(msg.getUserId());
        LocalDateTime now = LocalDateTime.now();
        // 原到期时间若还没过期，则从原到期时间继续累加；已过期则从现在开始
        LocalDateTime base = user.getMemberExpire();
        LocalDateTime newExpire = (base != null && base.isAfter(now)) ? base : now;
        newExpire = newExpire.plusDays(msg.getDays());
        int newLevel = Math.max(user.getMemberLevel() == null ? 0 : user.getMemberLevel(), msg.getLevel());

        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setMemberLevel(newLevel);
        updateUser.setMemberExpire(newExpire);
        userMapper.updateById(updateUser);
        log.info("会员升级成功, userId={}, orderId={}, 新等级={}, 到期时间={}", msg.getUserId(), orderId, newLevel, newExpire);

        // 4. 处理成功后写入幂等标记（TTL 1 天，足够覆盖消息重试窗口）
        stringRedisTemplate.opsForValue().set(idempotentKey, "1", Duration.ofDays(1));
    }

    @Override
    public void closeExpiredOrder(String orderId) {
        // 1. 查订单；不存在直接返回（消息幂等：重复投递也不会报错）
        MemberOrder order = orderMapper.selectOne(new LambdaQueryWrapper<MemberOrder>()
                .eq(MemberOrder::getOrderId, orderId));
        if (order == null) {
            log.warn("关单消息对应订单不存在, orderId={}", orderId);
            return;
        }
        // 2. 只有"待支付"的订单才需要关闭；已支付/已关闭的直接跳过
        if (order.getStatus() != 0) {
            log.info("订单状态已是 {}, 无需关闭, orderId={}", order.getStatus(), orderId);
            return;
        }

        // 3. 关闭订单：状态 0 -> 3（已过期），WHERE status=0 保证只关闭一次
        MemberOrder update = new MemberOrder();
        update.setStatus(3);
        update.setCloseTime(LocalDateTime.now());
        int rows = orderMapper.update(update, new LambdaQueryWrapper<MemberOrder>()
                .eq(MemberOrder::getId, order.getId())
                .eq(MemberOrder::getStatus, 0));
        if (rows > 0) {
            // 4. 同步刷新缓存
            order.setStatus(3);
            order.setCloseTime(update.getCloseTime());
            cacheOrder(order);
            log.info("延时关单成功, orderId={}", orderId);
        }
    }

    @Override
    public MemberInfoVO getMemberInfo(Long userId) {
        User user = userMapper.selectById(userId);
        MemberInfoVO vo = new MemberInfoVO();
        vo.setUserId(userId);

        int level = user.getMemberLevel() == null ? 0 : user.getMemberLevel();
        vo.setLevel(level);
        MemberLevel.Info info = MemberLevel.of(level);
        vo.setLevelName(info != null ? info.name : "普通会员");
        vo.setMemberExpire(user.getMemberExpire());

        // 是否有效会员：到期时间晚于当前时间才算
        boolean isMember = user.getMemberExpire() != null && user.getMemberExpire().isAfter(LocalDateTime.now());
        vo.setIsMember(isMember);
        vo.setRemainDays(isMember
                ? ChronoUnit.DAYS.between(LocalDateTime.now(), user.getMemberExpire())
                : 0L);
        return vo;
    }

    // ==================== 私有工具方法 ====================

    /** 生成业务订单号：时间戳 + 4 位随机数，如 202608211530001234 */
    private String generateOrderId() {
        return DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    // ==================== 可靠发送封装（发送失败自动进补偿队列） ====================

    /**
     * 发送"延时关单"TTL 消息：带 per-message 过期时间。
     * 发送抛异常（如 MQ 暂不可用）时记入失败补偿队列，不阻塞下单主流程。
     */
    private void sendOrderCloseMessage(String orderId) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitConstants.EX_ORDER_TTL,
                    RabbitConstants.RK_ORDER_CREATE,
                    orderId,
                    // 给这条消息单独设置过期时间（毫秒）
                    message -> {
                        message.getMessageProperties().setExpiration(String.valueOf(ttlMs));
                        return message;
                    },
                    // CorrelationData id 带类型前缀，confirm 失败时据此自动补偿
                    new CorrelationData(RabbitConstants.FAIL_TYPE_CLOSE + ":" + orderId));
        } catch (Exception e) {
            log.error("延时关单消息发送异常，已记入补偿队列, orderId={}, 原因={}", orderId, e.getMessage());
            recordFail(RabbitConstants.FAIL_TYPE_CLOSE, orderId);
        }
    }

    /**
     * 发送"会员升级"消息。发送抛异常时记入失败补偿队列，不阻塞支付主流程。
     */
    private void sendMemberUpgradeMessage(UpgradeMessage msg) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitConstants.EX_MEMBER,
                    RabbitConstants.RK_MEMBER_UPGRADE,
                    msg,
                    new CorrelationData(RabbitConstants.FAIL_TYPE_UPGRADE + ":" + msg.getOrderId()));
        } catch (Exception e) {
            log.error("会员升级消息发送异常，已记入补偿队列, orderId={}, 原因={}", msg.getOrderId(), e.getMessage());
            recordFail(RabbitConstants.FAIL_TYPE_UPGRADE, msg.getOrderId());
        }
    }

    /** 写入失败补偿队列：元素格式 {类型}|{orderId}，MqCompensateTask 每 30 秒取出重发 */
    private void recordFail(String type, String orderId) {
        try {
            stringRedisTemplate.opsForList().leftPush(RabbitConstants.FAIL_LIST_KEY, type + "|" + orderId);
        } catch (Exception e) {
            // Redis 也不可用：只能记日志（该场景下系统本身已处于异常状态）
            log.error("MQ 失败补偿写入 Redis 异常, {}|{}, 原因={}", type, orderId, e.getMessage());
        }
    }

    // ==================== MQ 失败补偿（供 MqCompensateTask 调用） ====================

    @Override
    public void resendCloseMessage(String orderId) {
        // 幂等判断：订单不存在或已支付/已关闭时无需再关单，直接丢弃
        MemberOrder order = orderMapper.selectOne(new LambdaQueryWrapper<MemberOrder>()
                .eq(MemberOrder::getOrderId, orderId));
        if (order == null) {
            log.warn("补偿关单：订单不存在，忽略, orderId={}", orderId);
            return;
        }
        if (order.getStatus() != 0) {
            log.info("补偿关单：订单状态已是 {}, 无需补发, orderId={}", order.getStatus(), orderId);
            return;
        }
        sendOrderCloseMessage(orderId);
    }

    @Override
    public void resendUpgradeMessage(String orderId) {
        // 幂等判断：只有"已支付"且"尚未升级成功"（无幂等标记）才需要补发升级
        String idempotentKey = RabbitConstants.IDEMPOTENT_PREFIX + orderId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(idempotentKey))) {
            log.info("补偿升级：订单 {} 已升级成功，忽略", orderId);
            return;
        }
        MemberOrder order = orderMapper.selectOne(new LambdaQueryWrapper<MemberOrder>()
                .eq(MemberOrder::getOrderId, orderId));
        if (order == null) {
            log.warn("补偿升级：订单不存在，忽略, orderId={}", orderId);
            return;
        }
        if (order.getStatus() != 1) {
            log.info("补偿升级：订单未支付，无需补发, orderId={}, status={}", orderId, order.getStatus());
            return;
        }
        // 从数据库订单重建消息体（模拟开关关闭：补偿路径不触发"故意报错"演示）
        UpgradeMessage msg = new UpgradeMessage();
        msg.setOrderId(orderId);
        msg.setUserId(order.getUserId());
        msg.setLevel(order.getLevel());
        msg.setDays(order.getDays());
        msg.setSimulateError(false);
        sendMemberUpgradeMessage(msg);
    }

    /** 订单写缓存：TTL 比关单延时多 10 秒，保证关单消息处理时缓存还在 */
    private void cacheOrder(MemberOrder order) {
        try {
            stringRedisTemplate.opsForValue().set(
                    RabbitConstants.CACHE_ORDER_PREFIX + order.getOrderId(),
                    objectMapper.writeValueAsString(order),
                    Duration.ofMillis(ttlMs + 10_000));
        } catch (Exception e) {
            // 缓存失败不影响主流程（降级为只查数据库）
            log.warn("订单写缓存失败, orderId={}, 原因={}", order.getOrderId(), e.getMessage());
        }
    }

    /** 查询订单：先读 Redis 缓存，缓存没有再查数据库并回填缓存（缓存穿透处理） */
    private MemberOrder getByIdFromCacheOrDb(String orderId) {
        String cacheKey = RabbitConstants.CACHE_ORDER_PREFIX + orderId;
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        if (json != null) {
            try {
                return objectMapper.readValue(json, MemberOrder.class);
            } catch (Exception e) {
                log.warn("订单缓存反序列化失败，回源数据库, orderId={}, 原因={}", orderId, e.getMessage());
            }
        }
        MemberOrder order = orderMapper.selectOne(new LambdaQueryWrapper<MemberOrder>()
                .eq(MemberOrder::getOrderId, orderId));
        if (order != null) {
            cacheOrder(order);
        }
        return order;
    }
}
