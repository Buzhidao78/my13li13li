package com.example.practice.dto;

import lombok.Data;

/**
 * 下单入参：前端选择会员等级后提交
 */
@Data
public class CreateOrderDTO {

    /** 购买的会员等级：1-白银 2-黄金 3-钻石（由后端校验合法性） */
    private Integer level;
}
