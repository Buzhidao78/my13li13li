package com.example.practice.vo;

import lombok.Data;

/**
 * 用户主页出参 VO：他人主页展示的用户信息
 * 在基础用户信息上补充了统计数字（视频数/粉丝数/关注数）和当前登录用户是否已关注
 */
@Data
public class UserProfileVO {

    private Long id;

    /** 昵称 */
    private String nickname;

    /** 头像地址 */
    private String avatar;

    /** 个性签名 */
    private String sign;

    /** 性别：0-未知 1-男 2-女 */
    private Integer gender;

    /** 已发布视频数 */
    private Long videoCount;

    /** 粉丝数 */
    private Long followerCount;

    /** 关注数 */
    private Long followingCount;

    /** 当前登录用户是否已关注 TA（未登录为 null/false） */
    private Boolean followed;
}
