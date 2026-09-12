package com.example.practice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.practice.common.Result;
import com.example.practice.security.LoginUser;
import com.example.practice.service.VideoService;
import com.example.practice.vo.VideoVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心控制器（需登录）：我的收藏、观看历史
 * 数据由视频服务提供（收藏/历史都是"用户-视频"关系）
 */
@RestController
@RequestMapping("/api/user/me")
public class UserCenterController {

    @Autowired
    private VideoService videoService;

    /**
     * 我的收藏（按收藏时间倒序）
     */
    @GetMapping("/favorites")
    public Result<IPage<VideoVO>> favorites(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "12") long size) {
        return Result.success(videoService.myFavorites(currentUserId(), page, size));
    }

    /**
     * 观看历史（按观看时间倒序）
     */
    @GetMapping("/history")
    public Result<IPage<VideoVO>> history(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "12") long size) {
        return Result.success(videoService.myHistory(currentUserId(), page, size));
    }

    /**
     * 删除单条观看历史（按 videoId，仅删除"我"的记录）
     */
    @DeleteMapping("/history/{videoId}")
    public Result<Void> deleteHistory(@PathVariable Long videoId) {
        videoService.deleteHistory(currentUserId(), videoId);
        return Result.success();
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        LoginUser loginUser = (LoginUser) auth.getPrincipal();
        return loginUser.getId();
    }
}
