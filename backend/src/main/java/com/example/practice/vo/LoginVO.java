package com.example.practice.vo;

import lombok.Data;

/**
 * 登录成功返回体：token + 用户信息
 */
@Data
public class LoginVO {

    /** JWT token，前端后续请求放进 Authorization 头 */
    private String token;

    /** 当前登录用户信息 */
    private UserVO user;

    public static LoginVO of(String token, UserVO user) {
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUser(user);
        return vo;
    }
}
