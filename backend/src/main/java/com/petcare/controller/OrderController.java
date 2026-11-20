package com.petcare.controller;

import com.petcare.common.PageResult;
import com.petcare.common.Result;
import com.petcare.dto.OrderCreateDTO;
import com.petcare.dto.ReviewDTO;
import com.petcare.service.OrderService;
import com.petcare.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 用户端订单接口。
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 下单。
     * 金额由后端按服务单价计算，前端传的金额一律忽略（防篡改）。
     */
    @PostMapping
    public Result<OrderVO> create(@RequestBody @Valid OrderCreateDTO dto) {
        return Result.success("预约成功", orderService.create(dto));
    }

    /** 我的订单列表，status 不传表示全部 */
    @GetMapping
    public Result<PageResult<OrderVO>> myOrders(@RequestParam(required = false) Integer status,
                                                @RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "5") int pageSize) {
        return Result.success(orderService.myOrders(status, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id) {
        return Result.success(orderService.detail(id));
    }

    /** 取消订单 */
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id,
                               @RequestParam(required = false) String reason) {
        orderService.cancel(id, reason);
        return Result.success("订单已取消", null);
    }

    /** 催单（5 分钟内只允许一次） */
    @PostMapping("/{id}/urge")
    public Result<Void> urge(@PathVariable Long id) {
        orderService.urge(id);
        return Result.success("已提醒商家，请耐心等待", null);
    }

    /** 提交评价 */
    @PostMapping("/review")
    public Result<Void> review(@RequestBody @Valid ReviewDTO dto) {
        orderService.review(dto);
        return Result.success("感谢你的评价", null);
    }
}
