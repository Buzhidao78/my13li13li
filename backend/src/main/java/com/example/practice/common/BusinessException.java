package com.example.practice.common;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务异常：业务规则不满足时抛出，由全局异常处理器统一转成 Result 返回
 * 例如：用户名已存在、验证码错误等
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessException extends RuntimeException {

    /** 错误码，默认 500 */
    private Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
