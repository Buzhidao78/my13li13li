package com.example.practice.service;

import java.util.List;

/**
 * 搜索辅助服务：热门搜索词 + 个人搜索历史
 * 视频本身的模糊搜索在 VideoService.search（复用视频 VO 转换逻辑）
 */
public interface SearchService {

    /**
     * 记录一次搜索：热词计数 +1；登录用户同时写入个人搜索历史（去重、只留最近 20 条）
     * @param userId 未登录传 null（只记热词，不记个人历史）
     */
    void record(String keyword, Long userId);

    /**
     * 热门搜索词 TopN（按搜索次数倒序）
     */
    List<String> hotKeywords(int topN);

    /**
     * 我的搜索历史（按最近搜索时间倒序，最多 20 条）
     */
    List<String> myHistory(Long userId);

    /**
     * 清空我的搜索历史
     */
    void clearHistory(Long userId);
}
