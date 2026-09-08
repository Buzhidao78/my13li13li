package com.example.practice.constant;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 会员等级定义：等级信息统一维护在这里（练习项目用常量写死，生产环境可放数据库/配置中心）
 */
public class MemberLevel {

    /** 普通用户（未开通会员） */
    public static final int NORMAL = 0;
    /** 白银会员 */
    public static final int SILVER = 1;
    /** 黄金会员 */
    public static final int GOLD = 2;
    /** 钻石会员 */
    public static final int DIAMOND = 3;

    /**
     * 单个等级的信息
     */
    public static class Info {
        /** 等级编号 */
        public final int level;
        /** 等级名称 */
        public final String name;
        /** 价格（元） */
        public final BigDecimal amount;
        /** 开通时长（天） */
        public final int days;
        /** 等级描述（展示用） */
        public final String remark;

        public Info(int level, String name, BigDecimal amount, int days, String remark) {
            this.level = level;
            this.name = name;
            this.amount = amount;
            this.days = days;
            this.remark = remark;
        }
    }

    /** 全部等级（下标即等级号） */
    private static final List<Info> LEVELS = Arrays.asList(
            new Info(NORMAL, "普通会员", new BigDecimal("0.00"), 0, "基础体验"),
            new Info(SILVER, "白银会员", new BigDecimal("30.00"), 30, "30 天会员权益"),
            new Info(GOLD, "黄金会员", new BigDecimal("88.00"), 90, "90 天会员权益，热门内容抢先看"),
            new Info(DIAMOND, "钻石会员", new BigDecimal("188.00"), 365, "全年会员，全部权益尽享")
    );

    private static final List<Info> BUYABLE = LEVELS.subList(1, LEVELS.size());

    /** 返回可购买的等级列表（排除普通会员） */
    public static List<Info> buyable() {
        return new ArrayList<>(BUYABLE);
    }

    /** 按等级号查信息，查不到返回 null */
    public static Info of(int level) {
        for (Info info : LEVELS) {
            if (info.level == level) {
                return info;
            }
        }
        return null;
    }

    /** 校验等级是否可购买（必须是 白银/黄金/钻石 之一） */
    public static boolean isValid(int level) {
        return level >= SILVER && level <= DIAMOND;
    }
}
