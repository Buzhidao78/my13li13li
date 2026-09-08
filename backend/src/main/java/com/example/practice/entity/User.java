package com.example.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体：对应数据库表 user
 * 既是数据库实体，也会在登录时封装成 LoginUser 交给 Spring Security 校验
 */
@Data
@TableName("user")
public class User {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名：账号密码登录用（可空） */
    private String username;

    /** 密码：BCrypt 加密存储（手机号验证码登录的用户可为空） */
    private String password;

    /** 手机号：验证码登录用（可空） */
    private String phone;

    /** 昵称 */
    private String nickname;

    /** 头像地址 */
    private String avatar;

    /** 性别：0-未知 1-男 2-女 */
    private Integer gender;

    /** 个性签名 */
    private String sign;

    /** 会员等级：0-普通 1-白银 2-黄金 3-钻石 */
    private Integer memberLevel;

    /** 会员到期时间（NULL 表示从未开通） */
    private LocalDateTime memberExpire;

    /** 角色：0-普通用户 1-管理员（可审核/下架他人视频） */
    private Integer role;

    /** 账号状态：0-正常 1-禁用 */
    private Integer status;

    /** 逻辑删除：0-正常 1-已删除（MyBatis-Plus 查询时自动过滤） */
    @TableLogic
    private Integer deleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
