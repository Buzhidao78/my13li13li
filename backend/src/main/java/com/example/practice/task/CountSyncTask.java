package com.example.practice.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.practice.entity.Video;
import com.example.practice.mapper.VideoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 计数落库定时任务
 * 播放/点赞走 Redis 增量（高并发扛得住），本任务定期把增量刷进 MySQL，防止 Redis 数据丢失
 * 刷新间隔 5 分钟：极端情况（Redis 崩溃）最多丢 5 分钟的增量数据，业务上可接受
 */
@Slf4j
@Component
public class CountSyncTask {

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 播放增量 key 前缀（与 VideoServiceImpl 保持一致） */
    private static final String KEY_PLAY = "video:play:";

    /** 点赞增量 key 前缀 */
    private static final String KEY_LIKE_COUNT = "video:like:count:";

    /**
     * 每 5 分钟执行一次（fixedDelay：等上一次执行完再计时，
     * 避免任务执行时间超过周期时两个实例并发扫同一批 key，导致增量重复落库）
     */
    @Scheduled(fixedDelay = 300_000)
    public void syncCounts() {
        syncToDb(KEY_PLAY, "play_count");
        syncToDb(KEY_LIKE_COUNT, "like_count");
    }

    /**
     * 把一类计数增量刷进数据库
     * 流程：KEYS 找到所有增量 key → 逐个读取增量 → 用 SQL 原子自增累加到 MySQL
     *       → 落库成功后用 DECRBY 把 Redis 里已落库的增量扣掉（key 不删除）
     * 为什么用"扣回"而不是"取出即删"：
     *   1. key 全程存在，读取与扣减之间新产生的播放保留在 key 里，不会被误删；
     *   2. 落库失败时 key 纹丝不动，下一轮任务取到同样的增量重试，无需回补逻辑，增量天然不丢；
     *   3. 唯一代价是极端情况下（扣减失败）增量可能重复落库——计数场景"宁可重复不可漏"。
     * 说明：练习场景数据量小用 KEYS；生产环境数据量大应改用 SCAN 游标遍历
     */
    private void syncToDb(String prefix, String column) {
        Set<String> keys = stringRedisTemplate.keys(prefix + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            String deltaStr = stringRedisTemplate.opsForValue().get(key);
            if (deltaStr == null || "0".equals(deltaStr)) {
                // 值为空或 0：没有增量，直接清掉 key 避免下次再扫
                stringRedisTemplate.delete(key);
                continue;
            }
            try {
                int delta = Integer.parseInt(deltaStr);
                Long videoId = Long.parseLong(key.substring(prefix.length()));
                // 视频不存在（已被删除）：增量失去意义，直接清理 key
                if (videoMapper.selectById(videoId) == null) {
                    stringRedisTemplate.delete(key);
                    continue;
                }
                // SQL 原子自增：count = count + delta，避免"先查后改"在并发下互相覆盖
                videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                        .eq(Video::getId, videoId)
                        .setSql("`" + column + "` = `" + column + "` + (" + delta + ")"));
                // 落库成功后才扣减：把已落库部分从 Redis 增量中扣除，key 保留给后续新播放继续累加。
                // 若扣减失败（Redis 抖动），最多导致该增量下一轮重复落库一次，符合"宁可重复不可漏"。
                // 单独捕获：扣减失败不影响其他 key 继续同步
                try {
                    stringRedisTemplate.opsForValue().decrement(key, delta);
                } catch (Exception ex) {
                    log.warn("计数落库成功但扣减增量失败，下一轮将重复落库，key={}", key);
                }
            } catch (NumberFormatException e) {
                log.warn("计数同步解析失败，key={}", key);
            }
        }
    }
}
