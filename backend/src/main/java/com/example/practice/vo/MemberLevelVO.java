package com.example.practice.vo;

import com.example.practice.constant.MemberLevel;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 会员等级视图对象：给前端充值页展示用
 */
@Data
public class MemberLevelVO {

    /** 等级编号 */
    private Integer level;

    /** 等级名称 */
    private String name;

    /** 价格（元） */
    private BigDecimal amount;

    /** 开通时长（天） */
    private Integer days;

    /** 等级描述 */
    private String remark;

    /** 是否当前选中（前端用） */
    private Boolean selected;

    /**
     * 从常量转换：返回全部可购买等级
     */
    public static List<MemberLevelVO> fromAll() {
        List<MemberLevelVO> list = new ArrayList<>();
        for (MemberLevel.Info info : MemberLevel.buyable()) {
            MemberLevelVO vo = new MemberLevelVO();
            vo.setLevel(info.level);
            vo.setName(info.name);
            vo.setAmount(info.amount);
            vo.setDays(info.days);
            vo.setRemark(info.remark);
            vo.setSelected(false);
            list.add(vo);
        }
        return list;
    }

    /** 按等级号转单个 VO */
    public static MemberLevelVO of(int level) {
        MemberLevel.Info info = MemberLevel.of(level);
        if (info == null) {
            return null;
        }
        MemberLevelVO vo = new MemberLevelVO();
        vo.setLevel(info.level);
        vo.setName(info.name);
        vo.setAmount(info.amount);
        vo.setDays(info.days);
        vo.setRemark(info.remark);
        vo.setSelected(false);
        return vo;
    }
}
