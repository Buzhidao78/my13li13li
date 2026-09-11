package com.example.practice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.practice.dto.DmSendDTO;
import com.example.practice.vo.DmConversationVO;
import com.example.practice.vo.DmMessageVO;
import com.example.practice.vo.DmPermissionVO;

/**
 * 私信（DM）服务：会话列表、聊天历史、发送、已读、未读总数、发送权限预判
 * 权限规则（抖音式）：任何登录用户都能给任何人发私信；
 * 但若双方不是互相关注，在对方回复之前只能发一条（互相关注则不限次）。
 */
public interface DmService {

    /**
     * 我的会话列表，按最后活跃时间倒序
     */
    IPage<DmConversationVO> conversations(Long userId, long page, long size);

    /**
     * 单会话消息历史，按 id 倒序分页（前端 reverse 后正序渲染，向上翻页加载更早）
     */
    IPage<DmMessageVO> messages(Long userId, Long peerId, long page, long size);

    /**
     * 发送私信：校验（存在性/不能发给自己/抖音规则）→ 落库 → 维护双方会话行
     * @return 落库后的消息 VO（含数据库生成的 id/createTime，前端立即上屏）
     */
    DmMessageVO sendMessage(Long senderId, DmSendDTO dto);

    /**
     * 标记某会话已读（进入聊天窗时调用）：unread_count 清零
     */
    void markRead(Long userId, Long peerId);

    /**
     * 我的私信未读总数（导航栏红点用）
     */
    long unreadTotal(Long userId);

    /**
     * 抖音规则预判：当前能否给对方发私信（前端禁用输入框用，不抛异常）
     */
    DmPermissionVO permission(Long userId, Long peerId);
}
