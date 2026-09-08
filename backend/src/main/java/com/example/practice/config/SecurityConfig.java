package com.example.practice.config;

import com.example.practice.security.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 核心配置
 * 职责：
 * 1. 定义哪些接口需要登录、哪些放行
 * 2. 注册 JWT 过滤器，让每个请求先校验 token
 * 3. 配置 BCrypt 密码加密器和 AuthenticationManager
 * 4. 配置未登录(401)、无权限(403)的统一 JSON 返回
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Autowired
    private RestAccessDeniedHandler restAccessDeniedHandler;

    /**
     * 密码加密器：BCrypt 单向加密，注册时加密存储，登录时比对
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器：账号密码登录时由它协调 UserDetailsService + PasswordEncoder 完成校验
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * 安全过滤器链：整个应用的安全规则都定义在这里
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 前后端分离 + JWT 无状态，关闭 CSRF 防护
                .csrf(AbstractHttpConfigurer::disable)
                // 不创建 Session（每次请求都靠 token 识别身份）
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 接口访问规则
                .authorizeHttpRequests(auth -> auth
                        // 登录、注册、发验证码等接口放行（不需要 token）
                        .requestMatchers("/api/auth/**").permitAll()
                        // 测试用的 hello 接口放行
                        .requestMatchers("/api/user/hello").permitAll()
                        // 视频模块：已发布列表/搜索/他人视频/详情/播放计数/评论列表公开（游客可看）；上传/我的/动态流/审核/互动需登录
                        .requestMatchers("/api/video/list",
                                "/api/video/search",
                                "/api/video/user/**",
                                "/api/video/detail/**",
                                "/api/video/*/play",
                                "/api/video/*/comments").permitAll()
                        // 搜索辅助：热词和记录搜索公开（游客也能搜，只记热词不记历史）；个人历史需登录
                        .requestMatchers("/api/search/hot",
                                "/api/search/record").permitAll()
                        // 用户主页公开（游客可看他人主页）；关注/粉丝列表等其余接口默认需登录
                        .requestMatchers("/api/user/*/profile").permitAll()
                        // 上传的视频文件静态资源放行（/upload/**）
                        .requestMatchers("/upload/**").permitAll()
                        // 其他所有 /api/** 接口都必须登录后才能访问
                        .anyRequest().authenticated())
                // 异常处理：未登录返回 401，无权限返回 403
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                // 在用户名密码过滤器之前执行 JWT 过滤器，先校验 token
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
