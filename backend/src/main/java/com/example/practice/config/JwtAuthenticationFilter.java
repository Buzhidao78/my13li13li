package com.example.practice.config;

import com.example.practice.security.UserDetailsServiceImpl;
import com.example.practice.util.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器：每个请求都会经过这里
 * 流程：从请求头取出 token → 解析校验 → 把用户信息放进 SecurityContext
 * 后续 Spring Security 就能判断"这个请求是谁发的、是否需要登录"
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 1. 从请求头获取 Authorization: Bearer <token>
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                // 2. 解析 token，取出用户 id（subject 里存的就是用户 id）
                Claims claims = jwtUtils.parseToken(token);
                Long userId = Long.valueOf(claims.getSubject());

                // 3. 按用户 id 查用户（能查到说明账号仍有效）
                UserDetails userDetails = userDetailsService.loadUserById(userId);

                // 4. 校验账号是否可用（被禁用的账号不允许访问）
                if (userDetails.isEnabled()) {
                    // 5. 把认证信息放进 SecurityContext，后续接口就能拿到当前用户
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch (Exception e) {
                // token 无效或已过期：不设置认证信息，由 Security 的 401 处理器返回未登录
                SecurityContextHolder.clearContext();
            }
        }
        // 6. 继续执行后续过滤器链
        chain.doFilter(request, response);
    }
}
