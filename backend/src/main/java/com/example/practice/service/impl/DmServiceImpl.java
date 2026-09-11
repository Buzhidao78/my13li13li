package com.example.practice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.practice.common.BusinessException;
import com.example.practice.dto.DmSendDTO;
import com.example.practice.entity.DmConversation;
import com.example.practice.entity.DmMessage;
import com.example.practice.entity.User;
import com.example.practice.mapper.DmConversationMapper;
import com.example.practice.mapper.DmMessageMapper;
import com.example.practice.mapper.UserMapper;
import com.example.practice.service.DmService;
import com.example.practice.service.FollowService;
import com.example.practice.vo.DmConversationVO;
import com.example.practice.vo.DmMessageVO;
import com.example.practice.vo.DmPermissionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 私信服务实现
 *
 * 表设计回顾（见 schema.sql）：
 * - dm_conversation：每个用户对每个对方存"一行自己的会话"，各存各的未读数与最后一条摘要，
 *   会话列表/未读总数都只需单表查询；
 * - dm_message：纯消息流水，不存已读状态（已读是会话级概念，由 unread_count 承载）。
 *
 * 抖音式发送规则（checkCanSend）：
 * - 互相关注 → 不限次；
 * - 非互关 → 会话中最后一条消息必须是"对方发给我的"才允许发：
 *   不存在（搭讪第一条）或最后一条是对方的（对方回复过）→ 放行；
 *   最后一条是我发的（对方还没回）→ 拒绝："对方回复你之前只能发送一条私信"。
 *   该实现天然覆盖"发一条→等回复→对方回了→又能发一条"的滑动语义，无需额外状态字段。
 *   并发权衡：极端并发下可能多发一条，练习项目不加锁（真实产品会用分布式锁/频控兜底）。
 */
@Service
public class DmServiceImpl implements DmService {

    /** 会话摘要最大长度（对应表字段 VARCHAR(100)） */
    private static final int SUMMARY_MAX_LEN = 100;

    /** 消息最大长度（对应表字段 VARCHAR(500)） */
    private static final int CONTENT_MAX_LEN = 500;

    @Autowired
    private DmConversationMapper conversationMapper;

    @Autowired
    private DmMessageMapper messageMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FollowService followService;

    @Override
    public IPage<DmConversationVO> conversations(Long userId, long page, long size) {
        // 会话列表：单表按 update_time 倒序一查即可（双行会话表设计的收益所在）
        Page<DmConversation> p = conversationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<DmConversation>()
                        .eq(DmConversation::getUserId, userId)
                        .orderByDesc(DmConversation::getUpdateTime));
        // 组装对方昵称/头像：沿用项目"convert 时单查"惯例（N+1，size=20 练习可接受；可优化为批量 in 查询）
        return p.convert(this::toConversationVO);
    }

    @Override
    public IPage<DmMessageVO> messages(Long userId, Long peerId, long page, long size) {
        // 安全校验：对方必须存在（防止任意 peerId 探测消息）
        requireUserExists(peerId);
        // 双向 OR 查询 pair 的消息流水，按 id 倒序分页（前端 reverse 后正序渲染）
        Page<DmMessage> p = messageMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<DmMessage>()
                        .and(w -> w
                                .and(w2 -> w2.eq(DmMessage::getSenderId, userId).eq(DmMessage::getReceiverId, peerId))
                                .or(w2 -> w2.eq(DmMessage::getSenderId, peerId).eq(DmMessage::getReceiverId, userId)))
                        .orderByDesc(DmMessage::getId));
        return p.convert(this::toMessageVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmMessageVO sendMessage(Long senderId, DmSendDTO dto) {
        // 1. 基础校验：接收者存在、不能发给自己、内容 1~500 字
        Long receiverId = dto.getReceiverId();
        if (receiverId == null) {
            throw new BusinessException("接收者不能为空");
        }
        requireUserExists(receiverId);
        if (receiverId.equals(senderId)) {
            throw new BusinessException("不能给自己发私信");
        }
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("私信内容不能为空");
        }
        if (content.length() > CONTENT_MAX_LEN) {
            throw new BusinessException("私信不能超过 " + CONTENT_MAX_LEN + " 字");
        }

        // 2. 抖音式权限校验（互关豁免，非互关单条限制）
        checkCanSend(senderId, receiverId);

        // 3. 消息落库
        DmMessage msg = new DmMessage();
        msg.setSenderId(senderId);
        msg.setReceiverId(receiverId);
        msg.setContent(content);
        messageMapper.insert(msg);

        // 4. 维护发送方的会话行（自己发的消息对自己当然是已读，unread_count 不变）
        LocalDateTime now = msg.getCreateTime() == null ? LocalDateTime.now() : msg.getCreateTime();
        upsertConversation(senderId, receiverId, msg, now, 0);

        // 5. 维护接收方的会话行（未读 +1）
        upsertConversation(receiverId, senderId, msg, now, 1);

        // 重新查询拿数据库默认值（create_time）
        DmMessage saved = messageMapper.selectById(msg.getId());
        return toMessageVO(saved);
    }

    @Override
    public void markRead(Long userId, Long peerId) {
        // 会话级已读：把我这一行的未读数清零；会话不存在时静默返回（如从 ?userId= 直开还没发过消息）
        DmConversation conv = conversationMapper.selectOne(new LambdaQueryWrapper<DmConversation>()
                .eq(DmConversation::getUserId, userId)
                .eq(DmConversation::getPeerId, peerId));
        if (conv != null && conv.getUnreadCount() != null && conv.getUnreadCount() > 0) {
            conv.setUnreadCount(0);
            conversationMapper.updateById(conv);
        }
    }

    @Override
    public long unreadTotal(Long userId) {
        // 未读总数 = 我所有会话行 unread_count 之和；用 selectList 后内存求和
        //（MyBatis-Plus 的 selectCount 只能数行数，SUM 需要自定义 SQL，这里保持无 XML 的项目惯例）
        return conversationMapper.selectList(new LambdaQueryWrapper<DmConversation>()
                        .eq(DmConversation::getUserId, userId)
                        .gt(DmConversation::getUnreadCount, 0)
                        .select(DmConversation::getUnreadCount))
                .stream().mapToLong(c -> c.getUnreadCount() == null ? 0 : c.getUnreadCount())
                .sum();
    }

    @Override
    public DmPermissionVO permission(Long userId, Long peerId) {
        requireUserExists(peerId);
        if (peerId.equals(userId)) {
            return DmPermissionVO.deny("不能给自己发私信");
        }
        // 互相关注 → 恒可发
        if (followService.isMutualFollowed(userId, peerId)) {
            return DmPermissionVO.ok();
        }
        // 非互关 → 看最后一条消息是谁发的
        DmMessage last = lastMessageOf(userId, peerId);
        if (last == null || last.getSenderId().equals(peerId)) {
            // 没发过（搭讪第一条）或最后一条是对方的（对方回复过）→ 可发
            return DmPermissionVO.ok();
        }
        // 最后一条是我发的、对方没回 → 限制
        return DmPermissionVO.deny("对方回复你之前只能发送一条私信");
    }

    // ==================== 私有方法 ====================

    /**
     * 抖音式发送校验：不能发时抛 BusinessException（permission 是它的非异常版本）
     */
    private void checkCanSend(Long senderId, Long receiverId) {
        if (followService.isMutualFollowed(senderId, receiverId)) {
            return; // 互关不限次
        }
        DmMessage last = lastMessageOf(senderId, receiverId);
        if (last != null && last.getSenderId().equals(senderId)) {
            // 非互关 + 最后一条是我发的（对方未回复）→ 拒绝
            throw new BusinessException("对方回复你之前只能发送一条私信");
        }
    }

    /** 查 pair 的最近一条消息（抖音规则判定用），无消息返回 null */
    private DmMessage lastMessageOf(Long userA, Long userB) {
        return messageMapper.selectOne(new LambdaQueryWrapper<DmMessage>()
                .and(w -> w
                        .and(w2 -> w2.eq(DmMessage::getSenderId, userA).eq(DmMessage::getReceiverId, userB))
                        .or(w2 -> w2.eq(DmMessage::getSenderId, userB).eq(DmMessage::getReceiverId, userA)))
                .orderByDesc(DmMessage::getId)
                .last("LIMIT 1"));
    }

    /** 校验用户存在，不存在抛业务异常 */
    private void requireUserExists(Long userId) {
        if (userMapper.selectById(userId) == null) {
            throw new BusinessException("用户不存在");
        }
    }

    /**
     * 维护会话行（upsert 语义）：
     * 存在则更新最后一条摘要/时间/未读数增量；不存在则插入。
     * 并发首条消息可能双方同时 insert 撞 uk 唯一约束导致整个事务回滚——
     * 上层表现为"发送失败请重试"，练习项目接受（真实产品会用 INSERT ... ON DUPLICATE KEY UPDATE）。
     *
     * @param owner        会话归属者（这一行是谁的）
     * @param peer         对方
     * @param msg          刚落库的消息
     * @param time         消息时间
     * @param unreadDelta  未读数增量：0=归属者自己发的（不增）；1=对方发来的（+1）
     */
    private void upsertConversation(Long owner, Long peer, DmMessage msg, LocalDateTime time, int unreadDelta) {
        DmConversation conv = conversationMapper.selectOne(new LambdaQueryWrapper<DmConversation>()
                .eq(DmConversation::getUserId, owner)
                .eq(DmConversation::getPeerId, peer));
        String summary = msg.getContent().length() > SUMMARY_MAX_LEN
                ? msg.getContent().substring(0, SUMMARY_MAX_LEN) : msg.getContent();
        if (conv == null) {
            conv = new DmConversation();
            conv.setUserId(owner);
            conv.setPeerId(peer);
            conv.setLastMessageId(msg.getId());
            conv.setLastContent(summary);
            conv.setLastTime(time);
            conv.setUnreadCount(unreadDelta);
            conv.setUpdateTime(time);
            conversationMapper.insert(conv);
        } else {
            conv.setLastMessageId(msg.getId());
            conv.setLastContent(summary);
            conv.setLastTime(time);
            conv.setUpdateTime(time);
            if (unreadDelta > 0) {
                conv.setUnreadCount((conv.getUnreadCount() == null ? 0 : conv.getUnreadCount()) + unreadDelta);
            }
            conversationMapper.updateById(conv);
        }
    }

    /** 会话实体 → VO（联查对方昵称头像） */
    private DmConversationVO toConversationVO(DmConversation conv) {
        DmConversationVO vo = new DmConversationVO();
        vo.setPeerId(conv.getPeerId());
        vo.setLastContent(conv.getLastContent());
        vo.setLastTime(conv.getLastTime());
        vo.setUnreadCount(conv.getUnreadCount());
        User peer = userMapper.selectById(conv.getPeerId());
        if (peer != null) {
            vo.setPeerNickname(peer.getNickname());
            vo.setPeerAvatar(peer.getAvatar());
        } else {
            vo.setPeerNickname("已注销用户");
        }
        return vo;
    }

    /** 消息实体 → VO */
    private DmMessageVO toMessageVO(DmMessage msg) {
        DmMessageVO vo = new DmMessageVO();
        vo.setId(msg.getId());
        vo.setSenderId(msg.getSenderId());
        vo.setReceiverId(msg.getReceiverId());
        vo.setContent(msg.getContent());
        vo.setCreateTime(msg.getCreateTime());
        return vo;
    }
}
