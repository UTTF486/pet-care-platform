package com.petcare.controller;

import com.petcare.common.PageResult;
import com.petcare.common.Result;
import com.petcare.service.OrderService;
import com.petcare.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台 - 订单审核与履约。
 * <p>
 * 接单 = 审核通过，指派服务师后订单进入"待服务"；
 * 拒单 = 取消订单并通知用户。
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    public Result<PageResult<OrderVO>> page(@RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(orderService.adminPage(status, keyword, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id) {
        return Result.success(orderService.detail(id));
    }

    /** 接单并指派服务师 */
    @PutMapping("/{id}/accept")
    public Result<Void> accept(@PathVariable Long id, @RequestParam Long servicerId) {
        orderService.accept(id, servicerId);
        return Result.success("接单成功，已通知用户", null);
    }

    /** 标记服务完成 */
    @PutMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id) {
        orderService.finish(id);
        return Result.success("订单已完成", null);
    }

    /** 拒单 */
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,
                               @RequestParam(required = false) String reason) {
        orderService.reject(id, reason);
        return Result.success("已拒单并通知用户", null);
    }
}
