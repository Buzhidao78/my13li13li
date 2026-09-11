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
    public boolean isMutualFollowed(Long userA, Long userB) {
        // 互相关注 = A→B 与 B→A 两条关系都存在，两次 selectCount 即可（练习数据量无需优化成一条 SQL）
        return isFollowed(userA, userB) && isFollowed(userB, userA);
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
        // 是否互相关注（仅登录且看别人时有意义，私信抖音规则豁免也依赖这个判定）
        vo.setMutualFollowed(currentUserId == null || currentUserId.equals(targetUserId)
                ? null : isMutualFollowed(currentUserId, targetUserId));
        return vo;
    }

    @Override
    public IPage<UserVO> followers(Long userId, Long currentUserId, long page, long size) {
        // 粉丝列表：查 user_follow 里 following_id = userId 的记录（关注了 TA 的人）
        Page<UserFollow> p = followMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowingId, userId)
                        .orderByDesc(UserFollow::getCreateTime));
        return p.convert(f -> toUserVO(userMapper.selectById(f.getFollowerId()), currentUserId));
    }

    @Override
    public IPage<UserVO> following(Long userId, Long currentUserId, long page, long size) {
        // 关注列表：查 user_follow 里 follower_id = userId 的记录（TA 关注的人）
        Page<UserFollow> p = followMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, userId)
                        .orderByDesc(UserFollow::getCreateTime));
        return p.convert(f -> toUserVO(userMapper.selectById(f.getFollowingId()), currentUserId));
    }

    /**
     * 用户实体 → VO（粉丝/关注列表场景）
     * 注意：这里不再 set phone——粉丝/关注列表是"半公开"数据（游客可访问），
     * 任何人都可通过列表拿到他人手机号属于敏感信息泄露（2026-09-11 修补）
     * @param currentUserId 当前登录用户，未登录为 null（null 时 mutual 不填充）
     */
    private UserVO toUserVO(User user, Long currentUserId) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setGender(user.getGender());
        vo.setSign(user.getSign());
        // 互相关注标识：仅登录用户看别人时填充；每项一次双向判定（N+1，练习数据量可接受）
        if (currentUserId != null && !currentUserId.equals(user.getId())) {
            vo.setMutual(isMutualFollowed(currentUserId, user.getId()));
        }
        return vo;
    }
}
