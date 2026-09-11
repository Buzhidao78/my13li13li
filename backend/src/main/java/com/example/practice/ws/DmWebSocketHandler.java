package com.example.practice.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 私信 WebSocket 处理器（原生 TextWebSocketHandler，不用 STOMP）
 *
 * 职责：
 * - 维护"在线用户 → 连接"映射（供后端主动推送）；
 * - 心跳：收到文本 "PING" 回 "PONG"（前端每 25s 发一次，防止 Vite 代理掐掉空闲连接）；
 * - 对外提供 sendToUser：给指定用户推送 JSON（新私信 DM_NEW）。
 *
 * 多端/重复连接策略：后者覆盖前者——同一用户新连接建立时主动关闭旧连接。
 * 这是"单浏览器练习场景"最简单的策略；真实产品会按 token 维度做多端同步。
 *
 * 注意：推送是"尽力而为"——sendToUser 全程 try-catch，失败只记日志不抛异常，
 * 消息的可靠存储由数据库保证，离线用户靠红点轮询兜底。
 */
@Component
public class DmWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DmWebSocketHandler.class);

    /** 心跳约定：客户端发 PING，服务端回 PONG */
    private static final String PING = "PING";
    private static final String PONG = "PONG";

    /** 在线用户映射：userId → 连接（后者覆盖前者） */
    private static final Map<Long, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    /**
     * JSON 序列化：必须注入 Spring 容器里的 ObjectMapper，而不是 new ObjectMapper()——
     * Spring Boot 自动配置的实例已注册 JavaTimeModule，能序列化 LocalDateTime；
     * 裸 new 的实例遇到 VO 里的 createTime 会抛
     * "Java 8 date/time type not supported by default" 导致推送静默失败
     */
    private final ObjectMapper objectMapper;

    public DmWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            // 理论上不会发生（握手拦截器已校验），防御性关闭
            closeQuietly(session);
            return;
        }
        // 后者覆盖前者：同用户新连接顶掉旧连接
        WebSocketSession old = SESSIONS.put(userId, session);
        if (old != null && old != session && old.isOpen()) {
            log.info("用户 {} 建立了新连接，关闭旧连接（后者覆盖前者）", userId);
            closeQuietly(old);
        }
        log.info("用户 {} 的 WebSocket 已连接，当前在线 {} 人", userId, SESSIONS.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            return;
        }
        // 关键：只有"当前映射里存的还是这个连接"才移除——
        // 覆盖策略下，旧连接的关闭回调可能晚于新连接建立，直接 remove 会误删新连接
        if (SESSIONS.get(userId) == session) {
            SESSIONS.remove(userId);
            log.info("用户 {} 的 WebSocket 已断开，当前在线 {} 人", userId, SESSIONS.size());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 心跳：PING → PONG；其余文本一律忽略（客户端不主动发业务消息，发送走 HTTP POST 保证事务）
        if (PING.equals(message.getPayload())) {
            sendText(session, PONG);
        }
    }

    /**
     * 给指定在线用户推送任意 payload（序列化为 JSON）
     * 用户不在线/推送失败：静默返回（消息已落库，靠红点轮询兜底）
     */
    public void sendToUser(Long userId, Object payload) {
        if (userId == null) {
            return;
        }
        WebSocketSession session = SESSIONS.get(userId);
        if (session == null || !session.isOpen()) {
            return; // 不在线，正常情况
        }
        try {
            sendText(session, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("推送 WebSocket 消息给用户 {} 失败：{}", userId, e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    /** 带同步的发送：Spring 标准建议对同一 session 的写操作加锁，防并发写异常 */
    private void sendText(WebSocketSession session, String text) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(text));
            }
        } catch (Exception e) {
            log.warn("WebSocket 发送失败（用户连接可能已断开）：{}", e.getMessage());
        }
    }

    /** 安静关闭连接（忽略异常） */
    private void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (Exception ignored) {
        }
    }
}
