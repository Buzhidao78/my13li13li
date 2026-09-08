package com.example.practice.security;

import com.example.practice.entity.User;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 登录用户：实现了 Spring Security 的 UserDetails 接口
 * Spring Security 通过它来获取账号密码、账号状态等信息
 */
@Data
public class LoginUser implements UserDetails {

    /** 用户 id */
    private Long id;

    /** 用户名（登录账号） */
    private String username;

    /** 密码（BCrypt 密文） */
    private String password;

    /** 账号状态：0-正常 1-禁用 */
    private Integer status;

    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 角色：0-普通用户 1-管理员（接口层按需做管理员校验） */
    private Integer role;

    public LoginUser(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.status = user.getStatus();
        this.nickname = user.getNickname();
        this.avatar = user.getAvatar();
        this.phone = user.getPhone();
        this.role = user.getRole();
    }

    /**
     * 是否管理员：角色为 1 的管理员
     * 供需要管理员权限的业务（如视频审核）直接判断
     */
    public boolean isAdmin() {
        return role != null && role == 1;
    }

    /** 权限列表：本练习暂不细分权限，返回空集合 */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    /** 账号是否未过期 */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /** 账号是否未锁定 */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /** 密码是否未过期 */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** 账号是否可用：status 为 0 时可用，为 1（禁用）时不允许登录 */
    @Override
    public boolean isEnabled() {
        return status != null && status == 0;
    }
}
