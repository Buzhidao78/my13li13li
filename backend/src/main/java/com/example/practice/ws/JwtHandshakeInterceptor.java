package com.example.practice.ws;

import com.example.practice.security.LoginUser;
import com.example.practice.security.UserDetailsServiceImpl;
import com.example.practice.util.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手鉴权拦截器
 *
 * 为什么 token 放 query 参数：浏览器的 WebSocket API 不允许自定义请求头
 * （new WebSocket(url) 无法设置 Authorization），所以只能拼在 URL 上：
 * ws://host/ws?token=xxx
 *
 * 为什么这里自己解析而不是复用 JwtAuthenticationFilter：
 * SecurityFilterChain 已对 /ws/** permitAll（握手是普通 HTTP GET，token 在 query
 * 不在 header，过滤器读不到），鉴权的唯一入口就是本拦截器——在握手阶段校验 token，
 * 校验通过把 userId 放进 attributes 供 Handler 使用，失败则返回 401 拒绝握手。
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 从 query 里解析 token 参数（用 UriComponentsBuilder，不手写 split）
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            // 解析 token 拿用户 id（subject），并确认账号仍然存在且可用
            Claims claims = jwtUtils.parseToken(token);
            Long userId = Long.valueOf(claims.getSubject());
            LoginUser loginUser = (LoginUser) userDetailsService.loadUserById(userId);
            // 握手通过：userId 存入 attributes，Handler 从这里取
            attributes.put("userId", loginUser.getId());
            return true;
        } catch (Exception e) {
            // token 无效/过期/用户不存在：拒绝握手（浏览器 console 可见 401）
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}
