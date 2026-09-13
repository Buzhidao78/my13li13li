package com.example.practice.listener;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.practice.constant.RabbitConstants;
import com.example.practice.dto.CoverExtractMessage;
import com.example.practice.entity.Video;
import com.example.practice.mapper.VideoMapper;
import com.example.practice.util.UploadUtils;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 视频封面异步抽帧消费者（监听 video.cover.extract.queue）
 *
 * 异步任务队列模式：上传接口只保存视频 + 落库 + 发消息，立即返回；
 * 本消费者负责调用 FFmpeg 抽帧并回写 cover_url，把 CPU 密集操作从 Web 线程移走。
 *
 * 可靠性：
 * - 手动 ACK：处理成功才确认，异常重回队列重试（最多 MAX_RETRY 次）
 * - 幂等：回写 cover_url 是 UPDATE 相同值，重复消费无副作用
 * - 兜底：抽帧失败返回 null 时直接确认（前端占位图兜底），封面失败绝不影响视频主流程
 */
@Slf4j
@Component
public class CoverExtractListener {

    /** 消费者重试上限：超过后放弃消息（避免无限重回队列） */
    private static final long MAX_RETRY = 3;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private UploadUtils uploadUtils;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RabbitConstants.Q_COVER_EXTRACT)
    public void handleExtract(CoverExtractMessage msg,
                              @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                              Channel channel) throws Exception {
        if (msg == null || msg.getVideoId() == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        Long videoId = msg.getVideoId();
        String retryKey = RabbitConstants.RETRY_PREFIX + videoId;
        try {
            // 抽帧：失败返回 null（内部已容错：超时/文件不存在等），不代表业务异常
            String coverUrl = uploadUtils.extractCoverFromVideo(msg.getVideoUrl());
            if (StringUtils.hasText(coverUrl)) {
                // 回写封面地址（幂等：重复消费也只会写成同一个值）
                videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                        .eq(Video::getId, videoId)
                        .set(Video::getCoverUrl, coverUrl));
                log.info("异步封面抽帧完成并回写, videoId={}, coverUrl={}", videoId, coverUrl);
            } else {
                // 抽帧失败（视频损坏/FFmpeg 缺失/超时）：封面保持 null，前端占位图兜底，不再重试
                log.warn("异步封面抽帧失败（已由占位图兜底）, videoId={}", videoId);
            }
            // 处理成功（含"抽帧失败但已兜底"）-> 手动 ACK
            channel.basicAck(deliveryTag, false);
            stringRedisTemplate.delete(retryKey);
        } catch (Exception e) {
            // 处理失败（回写数据库异常等）：统计重试次数，未超限则重回队列
            Long retry = stringRedisTemplate.opsForValue().increment(retryKey);
            if (retry <= MAX_RETRY) {
                log.error("封面抽帧处理失败（第{}次），消息重回队列, videoId={}, 原因={}", retry, videoId, e.getMessage());
                channel.basicNack(deliveryTag, false, true);
            } else {
                log.error("封面抽帧重试超过{}次上限，放弃该消息, videoId={}", MAX_RETRY, videoId);
                stringRedisTemplate.delete(retryKey);
                channel.basicAck(deliveryTag, false);
            }
        }
    }
}
