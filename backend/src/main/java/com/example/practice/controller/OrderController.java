package com.example.practice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.practice.common.Result;
import com.example.practice.dto.CreateOrderDTO;
import com.example.practice.security.LoginUser;
import com.example.practice.service.OrderService;
import com.example.practice.vo.MemberOrderVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单控制器：会员充值下单、模拟支付、查询订单
 * 所有接口都需要登录（JWT 认证），当前用户从 SecurityContext 获取
 */
@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 创建订单（充值下单）
     * 触发 Redis 分布式锁防重复 + 订单缓存 + TTL 延时关单消息
     */
    @PostMapping("/create")
    public Result<MemberOrderVO> create(@RequestBody CreateOrderDTO dto) {
        return Result.success(orderService.createOrder(currentUserId(), dto.getLevel()));
    }

    /**
     * 模拟支付：支付成功后发送"会员升级"MQ 消息（异步处理）
     * simulateError=true 时消费者会故意报错，用于演示"消息重回队列"
     */
    @PostMapping("/pay/{orderId}")
    public Result<MemberOrderVO> pay(@PathVariable String orderId,
                                     @RequestParam(defaultValue = "false") boolean simulateError) {
        return Result.success(orderService.payOrder(currentUserId(), orderId, simulateError));
    }

    /**
     * 查询订单详情（优先走 Redis 缓存，减轻 MySQL 压力）
     */
    @GetMapping("/{orderId}")
    public Result<MemberOrderVO> detail(@PathVariable String orderId) {
        return Result.success(orderService.getOrder(currentUserId(), orderId));
    }

    /**
     * 分页查询当前用户的订单列表（倒序）
     * 返回 IPage<MemberOrderVO>：records=当前页数据，total=总条数
     */
    @GetMapping("/my")
    public Result<IPage<MemberOrderVO>> myOrders(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "5") long size) {
        return Result.success(orderService.listMyOrders(currentUserId(), page, size));
    }

    /**
     * 查询当前用户最新的"待支付"订单（没有则 data 为 null）
     * 目的：无论订单列表翻到第几页，页面顶部的"继续支付"入口都能找到待支付订单
     */
    @GetMapping("/pending")
    public Result<MemberOrderVO> pending() {
        return Result.success(orderService.getPendingOrder(currentUserId()));
    }

    /** 从 Spring Security 上下文中取出当前登录用户的 id */
    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getId();
    }
}
