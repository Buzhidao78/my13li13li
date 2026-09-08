package com.example.practice.controller;

import com.example.practice.common.Result;
import com.example.practice.security.LoginUser;
import com.example.practice.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索辅助控制器：热词 + 个人搜索历史
 * 视频本身的模糊搜索在 VideoController 的 GET /api/video/search
 * 接口鉴权规则（SecurityConfig 中配置）：
 * - 公开：GET /api/search/hot、POST /api/search/record（游客也能搜，只记热词不记历史）
 * - 需登录：GET /api/search/history、DELETE /api/search/history
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired
    private SearchService searchService;

    /**
     * 热门搜索词 Top10（公开）
     */
    @GetMapping("/hot")
    public Result<List<String>> hot() {
        return Result.success(searchService.hotKeywords(10));
    }

    /**
     * 记录一次搜索（公开）：热词计数 +1；登录用户同时写个人历史
     */
    @PostMapping("/record")
    public Result<Void> record(@RequestParam String keyword) {
        searchService.record(keyword, currentUserIdOrNull());
        return Result.success();
    }

    /**
     * 我的搜索历史（需登录）
     */
    @GetMapping("/history")
    public Result<List<String>> history() {
        return Result.success(searchService.myHistory(currentUserId()));
    }

    /**
     * 清空我的搜索历史（需登录）
     */
    @DeleteMapping("/history")
    public Result<Void> clearHistory() {
        searchService.clearHistory(currentUserId());
        return Result.success();
    }

    private Long currentUserId() {
        return currentUserIdOrNull();
    }

    /** 获取当前登录用户 ID，未登录返回 null（公开接口用） */
    private Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }
}
