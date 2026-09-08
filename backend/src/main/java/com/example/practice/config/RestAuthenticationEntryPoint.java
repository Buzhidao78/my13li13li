package com.example.practice.config;

import com.example.practice.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未登录处理器：访问需要登录的接口但没有有效 token 时，统一返回 401 JSON
 * 注意：这里不能走全局异常处理器，因为 Spring Security 在进入 Controller 之前就拦截了
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                OBJECT_MAPPER.writeValueAsString(Result.error(401, "未登录或登录已过期")));
    }
}
