package com.example.practice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.practice.vo.UserProfileVO;
import com.example.practice.vo.UserVO;

/**
 * 关注服务：关注/取关、关注状态、用户主页信息、粉丝/关注列表
 */
public interface FollowService {

    /**
     * 关注 / 取消关注（切换）
     * @param followerId  操作方（当前登录用户）
     * @param followingId 被关注者
     * @return 操作后的状态：true=已关注，false=已取消
     */
    boolean toggleFollow(Long followerId, Long followingId);

    /**
     * 当前用户是否已关注对方
     */
    boolean isFollowed(Long followerId, Long followingId);

    /**
     * 用户主页信息：基础资料 + 视频数/粉丝数/关注数 + 当前用户是否已关注
     * @param currentUserId 当前登录用户 ID，未登录传 null
     */
    UserProfileVO profile(Long targetUserId, Long currentUserId);

    /**
     * 粉丝列表（关注了 targetUserId 的人），按关注时间倒序
     */
    IPage<UserVO> followers(Long userId, long page, long size);

    /**
     * 关注列表（targetUserId 关注的人），按关注时间倒序
     */
    IPage<UserVO> following(Long userId, long page, long size);
}
