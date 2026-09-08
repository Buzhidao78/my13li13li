package com.example.practice.controller;

import com.example.practice.common.Result;
import com.example.practice.dto.LoginDTO;
import com.example.practice.dto.PhoneLoginDTO;
import com.example.practice.dto.RegisterDTO;
import com.example.practice.dto.SendCodeDTO;
import com.example.practice.service.AuthService;
import com.example.practice.vo.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器：注册、登录、验证码（这些接口在 SecurityConfig 中已放行，不需要 token）
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /** 用户注册 */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody RegisterDTO dto) {
        authService.register(dto);
        return Result.success();
    }

    /** 账号密码登录 */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    /** 手机号验证码登录 */
    @PostMapping("/login/phone")
    public Result<LoginVO> loginByPhone(@RequestBody PhoneLoginDTO dto) {
        return Result.success(authService.loginByPhone(dto));
    }

    /** 发送短信验证码（练习环境仅打印到控制台） */
    @PostMapping("/code")
    public Result<Void> sendCode(@RequestBody SendCodeDTO dto) {
        authService.sendCode(dto.getPhone());
        return Result.success();
    }
}
