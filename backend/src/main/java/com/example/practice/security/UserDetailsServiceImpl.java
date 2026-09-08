package com.example.practice.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.practice.entity.User;
import com.example.practice.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 用户详情服务：实现 Spring Security 的 UserDetailsService
 * 登录时 Spring Security 会调用 loadUserByUsername 按用户名查数据库
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 按用户名查用户（MyBatis-Plus 会自动过滤逻辑删除的记录）
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return new LoginUser(user);
    }

    /**
     * 按用户 id 查用户
     * 说明：JWT 过滤器解析 token 后调用。用 id 而不是用户名，
     * 这样"纯手机号验证码注册"（没有用户名）的用户也能正常鉴权
     */
    public UserDetails loadUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return new LoginUser(user);
    }
}
