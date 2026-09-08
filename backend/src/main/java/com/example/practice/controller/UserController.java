package com.example.practice.controller;

import com.example.practice.common.Result;
import com.example.practice.security.LoginUser;
import com.example.practice.vo.UserVO;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制器：当前登录用户信息
 * 说明：早期联调用的 list（返回全表含密码哈希）/ add（明文密码入库）演示接口已删除，
 * 防止敏感信息泄露；新增用户请走 /api/auth/register
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    /** 测试接口：不依赖数据库，先验证前后端联调 */
    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("Hello, Full-Stack Practice!");
    }

    /** 获取当前登录用户信息（需要 token，验证 JWT 过滤器是否生效） */
    @GetMapping("/me")
    public Result<UserVO> me() {
        // 登录后，JwtAuthenticationFilter 会把 LoginUser 放进 SecurityContext
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return Result.success(UserVO.from(loginUser));
    }
}
