package com.example.practice.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：把抛出的异常统一转换成 Result 格式返回给前端
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：返回业务提示信息 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 账号密码错误（Spring Security 抛出）：给出友好的提示，不暴露堆栈 */
    @ExceptionHandler(BadCredentialsException.class)
    public Result<Void> handleBadCredentialsException(BadCredentialsException e) {
        return Result.error("用户名或密码错误");
    }

    /** 兜底：其他未知异常，记录日志并返回通用提示 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统繁忙，请稍后再试");
    }
}
