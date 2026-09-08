package com.example.practice.dto;

import lombok.Data;

/**
 * 注册请求参数
 */
@Data
public class RegisterDTO {

    /** 用户名 */
    private String username;

    /** 密码（明文，后端会用 BCrypt 加密） */
    private String password;

    /** 手机号 */
    private String phone;

    /** 手机号验证码（注册时校验手机号归属） */
    private String code;

    /** 昵称（可选，默认用手机尾号） */
    private String nickname;
}
