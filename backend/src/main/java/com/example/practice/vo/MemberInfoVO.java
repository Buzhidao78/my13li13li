package com.example.practice.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员信息视图对象：查询当前用户会员状态时返回
 */
@Data
public class MemberInfoVO {

    /** 用户 id */
    private Long userId;

    /** 会员等级：0-普通 1-白银 2-黄金 3-钻石 */
    private Integer level;

    /** 会员等级名称 */
    private String levelName;

    /** 会员到期时间 */
    private LocalDateTime memberExpire;

    /** 是否有效会员（到期时间在未来才算有效） */
    private Boolean isMember;

    /** 剩余天数（取整） */
    private Long remainDays;
}
