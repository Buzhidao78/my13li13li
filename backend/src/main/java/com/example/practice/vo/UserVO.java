package com.example.practice.vo;

import com.example.practice.security.LoginUser;
import lombok.Data;

/**
 * 用户信息视图：返回给前端的用户信息（绝不含密码）
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String phone;
    private Integer gender;
    private String sign;

    /** 角色：0-普通用户 1-管理员（前端可据此隐藏/展示管理入口） */
    private Integer role;

    /** 从登录用户对象转换（过滤掉密码等敏感字段） */
    public static UserVO from(LoginUser loginUser) {
        UserVO vo = new UserVO();
        vo.setId(loginUser.getId());
        vo.setUsername(loginUser.getUsername());
        vo.setNickname(loginUser.getNickname());
        vo.setAvatar(loginUser.getAvatar());
        vo.setPhone(loginUser.getPhone());
        vo.setRole(loginUser.getRole());
        return vo;
    }
}
