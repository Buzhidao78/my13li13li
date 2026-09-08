package com.example.practice.service;

import com.example.practice.dto.LoginDTO;
import com.example.practice.dto.PhoneLoginDTO;
import com.example.practice.dto.RegisterDTO;
import com.example.practice.vo.LoginVO;

/**
 * 认证服务：注册、登录、验证码
 */
public interface AuthService {

    /**
     * 用户注册：校验唯一性 + BCrypt 加密密码
     */
    void register(RegisterDTO dto);

    /**
     * 账号密码登录：交给 Spring Security AuthenticationManager 校验
     */
    LoginVO login(LoginDTO dto);

    /**
     * 手机号验证码登录：校验 Redis 中的验证码，不存在则自动注册
     */
    LoginVO loginByPhone(PhoneLoginDTO dto);

    /**
     * 发送短信验证码：存入 Redis（5 分钟有效，60 秒防重复）
     */
    void sendCode(String phone);
}
