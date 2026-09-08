package com.example.practice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.practice.common.Result;
import com.example.practice.security.LoginUser;
import com.example.practice.service.FollowService;
import com.example.practice.vo.UserProfileVO;
import com.example.practice.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 关注控制器
 * 注意：不用类级 @RequestMapping，因为 profile 接口路径是 /api/user/{id}/profile，
 *      与方法级路径写完整路径，避免类前缀拼接错误
 * 接口鉴权规则（SecurityConfig 中配置）：
 * - 公开：GET /api/user/{userId}/profile（用户主页，游客可看）
 * - 需登录：POST /api/follow/{userId}、GET /api/follow/status/{userId}、
 *           GET /api/follow/{userId}/followers、GET /api/follow/{userId}/following
 */
@RestController
public class FollowController {

    @Autowired
    private FollowService followService;

    /**
     * 关注 / 取消关注（切换），返回最新状态：true=已关注，false=已取消
     */
    @PostMapping("/api/follow/{userId}")
    public Result<Boolean> toggle(@PathVariable Long userId) {
        return Result.success(followService.toggleFollow(currentUserId(), userId));
    }

    /**
     * 当前登录用户是否已关注对方
     */
    @GetMapping("/api/follow/status/{userId}")
    public Result<Boolean> status(@PathVariable Long userId) {
        return Result.success(followService.isFollowed(currentUserId(), userId));
    }

    /**
     * 粉丝列表（关注了该用户的人），按关注时间倒序
     */
    @GetMapping("/api/follow/{userId}/followers")
    public Result<IPage<UserVO>> followers(@PathVariable Long userId,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "12") long size) {
        return Result.success(followService.followers(userId, page, size));
    }

    /**
     * 关注列表（该用户关注的人），按关注时间倒序
     */
    @GetMapping("/api/follow/{userId}/following")
    public Result<IPage<UserVO>> following(@PathVariable Long userId,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "12") long size) {
        return Result.success(followService.following(userId, page, size));
    }

    /**
     * 用户主页信息（公开接口，游客可看）：资料 + 视频数/粉丝数/关注数 + 是否已关注
     * 放在这里而不是 UserController，是因为"是否已关注"是关注模块的核心字段
     */
    @GetMapping("/api/user/{userId}/profile")
    public Result<UserProfileVO> profile(@PathVariable Long userId) {
        return Result.success(followService.profile(userId, currentUserIdOrNull()));
    }

    /** 获取当前登录用户 ID（必须登录的接口用） */
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
