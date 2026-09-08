package com.example.practice.dto;

import lombok.Data;

/**
 * 手机号验证码登录请求参数
 */
@Data
public class PhoneLoginDTO {

    /** 手机号 */
    private String phone;

    /** 短信验证码 */
    private String code;
}
