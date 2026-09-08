package com.example.practice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.practice.common.BusinessException;
import com.example.practice.constant.NotificationType;
import com.example.practice.constant.VideoStatus;
import com.example.practice.entity.User;
import com.example.practice.entity.UserFollow;
import com.example.practice.entity.Video;
import com.example.practice.mapper.UserFollowMapper;
import com.example.practice.mapper.UserMapper;
import com.example.practice.mapper.VideoMapper;
import com.example.practice.service.FollowService;
import com.example.practice.service.NotificationService;
import com.example.practice.vo.UserProfileVO;
import com.example.practice.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 关注服务实现
 * 关系表 user_follow 唯一约束 (follower_id, following_id) 防重复关注：
 * - 关注 = 查不到关系就 insert
 * - 取关 = 查得到关系就 delete
 * 关注成功时给被关注者发一条站内通知（type=关注）
 */
@Service
public class FollowServiceImpl implements FollowService {

    @Autowired
    private UserFollowMapper followMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFollow(Long followerId, Long followingId) {
        // 基础校验：不能关注自己、对方必须存在
        if (followerId.equals(followingId)) {
            throw new BusinessException("不能关注自己");
        }
        User target = userMapper.selectById(followingId);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }

        // 查已有关系：有则取关（删关系），无则关注（插关系 + 发通知）
        UserFollow exist = followMapper.selectOne(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFollowingId, followingId));
        if (exist == null) {
            UserFollow f = new UserFollow();
            f.setFollowerId(followerId);
            f.setFollowingId(followingId);
            followMapper.insert(f);
            // 关注成功：通知被关注者（内部会过滤"自己关注自己"的情况）
            notificationService.notify(followingId, followerId, NotificationType.FOLLOW, null, null);
            return true;
        } else {
            followMapper.deleteById(exist.getId());
            return false;
        }
    }

    @Override
    public boolean isFollowed(Long followerId, Long followingId) {
        return followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFollowingId, followingId)) > 0;
    }

    @Override
    public UserProfileVO profile(Long targetUserId, Long currentUserId) {
        User user = userMapper.selectById(targetUserId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setSign(user.getSign());
        vo.setGender(user.getGender());
        // 统计：已发布视频数（他人主页只看已发布，待审核/驳回不展示）
        vo.setVideoCount(videoMapper.selectCount(new LambdaQueryWrapper<Video>()
                .eq(Video::getUserId, targetUserId)
                .eq(Video::getStatus, VideoStatus.PUBLISHED)));
        // 统计：粉丝数 = 关注了这个人的记录数；关注数 = 这个人关注别人的记录数
        vo.setFollowerCount(followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowingId, targetUserId)));
        vo.setFollowingCount(followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, targetUserId)));
        // 当前登录用户是否已关注（未登录不查，返回 null）
        vo.setFollowed(currentUserId == null ? null : isFollowed(currentUserId, targetUserId));
        return vo;
    }

    @Override
    public IPage<UserVO> followers(Long userId, long page, long size) {
        // 粉丝列表：查 user_follow 里 following_id = userId 的记录（关注了 TA 的人）
        Page<UserFollow> p = followMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowingId, userId)
                        .orderByDesc(UserFollow::getCreateTime));
        return p.convert(f -> toUserVO(userMapper.selectById(f.getFollowerId())));
    }

    @Override
    public IPage<UserVO> following(Long userId, long page, long size) {
        // 关注列表：查 user_follow 里 follower_id = userId 的记录（TA 关注的人）
        Page<UserFollow> p = followMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, userId)
                        .orderByDesc(UserFollow::getCreateTime));
        return p.convert(f -> toUserVO(userMapper.selectById(f.getFollowingId())));
    }

    /** 用户实体 → VO（只暴露非敏感字段） */
    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setGender(user.getGender());
        vo.setSign(user.getSign());
        return vo;
    }
}
