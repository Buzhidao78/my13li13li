package com.example.practice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.practice.common.Result;
import com.example.practice.dto.DmSendDTO;
import com.example.practice.security.LoginUser;
import com.example.practice.service.DmService;
import com.example.practice.vo.DmConversationVO;
import com.example.practice.vo.DmMessageVO;
import com.example.practice.vo.DmPermissionVO;
import com.example.practice.ws.DmWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 私信控制器
 * 注意：不用类级 @RequestMapping，方法上写完整路径（与 FollowController 风格一致）
 * 所有接口均需登录（SecurityConfig 默认 anyRequest().authenticated() 覆盖）
 *
 * 发送链路的实时推送：sendMessage 是事务方法，Controller 在它成功返回之后
 * 再调 WebSocket 推送——保证"先落库、后推送"，推送失败也不影响消息持久化。
 */
@RestController
public class DmController {

    @Autowired
    private DmService dmService;

    @Autowired
    private DmWebSocketHandler dmWebSocketHandler;

    /**
     * 我的会话列表，按最后活跃时间倒序（消息中心左侧列表）
     */
    @GetMapping("/api/dm/conversations")
    public Result<IPage<DmConversationVO>> conversations(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.success(dmService.conversations(currentUserId(), page, size));
    }

    /**
     * 单会话消息历史，按 id 倒序分页（前端 reverse 后正序渲染，向上翻页加载更早）
     */
    @GetMapping("/api/dm/messages")
    public Result<IPage<DmMessageVO>> messages(@RequestParam Long peerId,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size) {
        return Result.success(dmService.messages(currentUserId(), peerId, page, size));
    }

    /**
     * 发送私信：落库成功后通过 WebSocket 实时推给在线的接收方
     */
    @PostMapping("/api/dm/send")
    public Result<DmMessageVO> send(@RequestBody DmSendDTO dto) {
        DmMessageVO vo = dmService.sendMessage(currentUserId(), dto);
        // 事务已提交（sendMessage 返回），再推送——接收方不在线则静默跳过，靠红点轮询兜底
        dmWebSocketHandler.sendToUser(vo.getReceiverId(),
                Map.of("type", "DM_NEW", "data", vo));
        return Result.success(vo);
    }

    /**
     * 标记某会话已读（进入聊天窗时调用）
     */
    @PostMapping("/api/dm/read/{peerId}")
    public Result<Void> markRead(@PathVariable Long peerId) {
        dmService.markRead(currentUserId(), peerId);
        return Result.success(null);
    }

    /**
     * 我的私信未读总数（导航栏红点）
     */
    @GetMapping("/api/dm/unread-total")
    public Result<Long> unreadTotal() {
        return Result.success(dmService.unreadTotal(currentUserId()));
    }

    /**
     * 抖音式发送权限预判（前端禁用输入框/显示提示用）
     */
    @GetMapping("/api/dm/permission")
    public Result<DmPermissionVO> permission(@RequestParam Long peerId) {
        return Result.success(dmService.permission(currentUserId(), peerId));
    }

    /** 获取当前登录用户 ID */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        LoginUser loginUser = (LoginUser) auth.getPrincipal();
        return loginUser.getId();
    }
}
