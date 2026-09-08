package com.example.practice.service.impl;

import com.example.practice.common.BusinessException;
import com.example.practice.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 搜索辅助服务实现（Redis ZSet）
 * 数据结构设计：
 * - search:hot 全站热词 ZSet：member=关键词，score=搜索次数，取 topN 用倒序
 * - search:history:{userId} 个人历史 ZSet：member=关键词，score=搜索时间戳，
 *   重复搜索更新时间戳（去重），每次写入后裁掉最旧的只留 20 条
 * 选择 ZSet 而不是 List/Hash 的原因：天然支持按 score 排序 + 去重（member 唯一）
 */
@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    /** 全站热词 ZSet key */
    private static final String KEY_HOT = "search:hot";

    /** 个人搜索历史 ZSet key 前缀 */
    private static final String KEY_HISTORY = "search:history:";

    /** 个人历史保留条数 */
    private static final int HISTORY_MAX = 20;

    /** 关键词最大长度（过长截断，防止无意义的长词污染热词榜） */
    private static final int KEYWORD_MAX_LEN = 30;

    /** 热词/历史 key 过期时间：30 天（每次写入刷新，防 key 无限膨胀；练习场景足够） */
    private static final java.time.Duration KEY_TTL = java.time.Duration.ofDays(30);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void record(String keyword, Long userId) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        // 截断 + 去空格，保证入库的每个词格式统一（去重才生效）
        String kw = keyword.trim();
        if (kw.length() > KEYWORD_MAX_LEN) {
            kw = kw.substring(0, KEYWORD_MAX_LEN);
        }
        // 1. 全站热词：搜索次数 +1
        stringRedisTemplate.opsForZSet().incrementScore(KEY_HOT, kw, 1);
        // 刷新热词 key 过期时间
        stringRedisTemplate.expire(KEY_HOT, KEY_TTL);

        // 2. 登录用户个人历史：member 用关键词（天然去重），score 用时间戳保证"最近搜索"在前
        if (userId != null) {
            String historyKey = KEY_HISTORY + userId;
            stringRedisTemplate.opsForZSet().add(historyKey, kw, System.currentTimeMillis());
            // 只保留最近 20 条：按 score 从小到大删掉最旧的多余记录（0 ~ -21 是最旧的）
            Set<String> all = stringRedisTemplate.opsForZSet().range(historyKey, 0, -1);
            if (all != null && all.size() > HISTORY_MAX) {
                stringRedisTemplate.opsForZSet().removeRange(historyKey, 0, all.size() - HISTORY_MAX - 1);
            }
            // 刷新个人历史 key 过期时间
            stringRedisTemplate.expire(historyKey, KEY_TTL);
        }
    }

    @Override
    public List<String> hotKeywords(int topN) {
        // score 倒序取前 topN 个关键词
        Set<String> hot = stringRedisTemplate.opsForZSet().reverseRange(KEY_HOT, 0, topN - 1);
        return hot == null ? List.of() : List.copyOf(hot);
    }

    @Override
    public List<String> myHistory(Long userId) {
        // score 倒序 = 最近搜索在前
        Set<String> history = stringRedisTemplate.opsForZSet()
                .reverseRange(KEY_HISTORY + userId, 0, HISTORY_MAX - 1);
        return history == null ? List.of() : List.copyOf(history);
    }

    @Override
    public void clearHistory(Long userId) {
        stringRedisTemplate.delete(KEY_HISTORY + userId);
    }
}
