package com.example.practice.config;

import com.example.practice.ws.DmWebSocketHandler;
import com.example.practice.ws.JwtHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册 /ws 端点（私信实时推送）
 *
 * 方案说明：用原生 TextWebSocketHandler 而不是 STOMP——
 * 本项目 WS 的唯一用途是"服务端 → 客户端"的新私信推送（点对点单一路径），
 * STOMP 的订阅/消息代理模型属于过度设计，原生 handler 更直观、教学也更清晰。
 *
 * 鉴权：JwtHandshakeInterceptor 在握手阶段校验 ?token=xxx（见该类注释）；
 * SecurityConfig 已对 /ws/** permitAll（握手是 HTTP GET，需先过安全链）。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private DmWebSocketHandler dmWebSocketHandler;

    @Autowired
    private JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(dmWebSocketHandler, "/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                // 练习项目放开跨域；生产环境应配置为前端的具体域名
                .setAllowedOrigins("*");
    }
}
