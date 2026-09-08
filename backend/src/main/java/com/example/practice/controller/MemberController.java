package com.example.practice.controller;

import com.example.practice.common.Result;
import com.example.practice.security.LoginUser;
import com.example.practice.service.OrderService;
import com.example.practice.vo.MemberInfoVO;
import com.example.practice.vo.MemberLevelVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会员控制器：查询会员等级、当前用户会员状态
 */
@RestController
@RequestMapping("/api/member")
public class MemberController {

    @Autowired
    private OrderService orderService;

    /** 查询可购买的会员等级列表（给充值页展示） */
    @GetMapping("/levels")
    public Result<List<MemberLevelVO>> levels() {
        return Result.success(MemberLevelVO.fromAll());
    }

    /** 查询当前用户的会员信息（等级、到期时间、剩余天数） */
    @GetMapping("/info")
    public Result<MemberInfoVO> info() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return Result.success(orderService.getMemberInfo(loginUser.getId()));
    }
}
