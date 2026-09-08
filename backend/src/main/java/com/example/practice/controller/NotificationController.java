package com.example.practice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.practice.common.Result;
import com.example.practice.security.LoginUser;
import com.example.practice.service.NotificationService;
import com.example.practice.vo.NotificationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知控制器（全部需登录，SecurityConfig 默认拦截）
 * 通知的生成不在控制器，而是由互动行为（点赞/评论/关注等）在服务层触发
 */
@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    /**
     * 分页查询我的通知（按时间倒序）
     */
    @GetMapping("/list")
    public Result<IPage<NotificationVO>> list(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "15") long size) {
        return Result.success(notificationService.list(currentUserId(), page, size));
    }

    /**
     * 未读通知数（导航栏红点用，进入页面时拉一次）
     */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(notificationService.unreadCount(currentUserId()));
    }

    /**
     * 全部标记已读
     */
    @PostMapping("/read-all")
    public Result<Void> readAll() {
        notificationService.readAll(currentUserId());
        return Result.success();
    }

    /**
     * 单条标记已读（服务层校验归属）
     */
    @PostMapping("/read/{id}")
    public Result<Void> readOne(@PathVariable Long id) {
        notificationService.readOne(currentUserId(), id);
        return Result.success();
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        LoginUser loginUser = (LoginUser) auth.getPrincipal();
        return loginUser.getId();
    }
}
