package com.example.practice.dto;

import lombok.Data;

/**
 * 发送验证码请求参数
 */
@Data
public class SendCodeDTO {

    /** 手机号 */
    private String phone;
}
