package com.petcare.controller;

import com.petcare.common.PageResult;
import com.petcare.common.Result;
import com.petcare.entity.ServiceItem;
import com.petcare.service.ServiceCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 后台 - 服务管理（上下架 / 增删改）。
 * 路径统一 /api/admin/**，拦截器会校验 role=1。
 */
@RestController
@RequestMapping("/api/admin/services")
@RequiredArgsConstructor
public class AdminServiceController {

    private final ServiceCatalogService serviceCatalogService;

    @GetMapping
    public Result<PageResult<ServiceItem>> page(@RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(serviceCatalogService.pageForAdmin(categoryId, keyword, status, pageNum, pageSize));
    }

    @PostMapping
    public Result<Long> save(@RequestBody @Valid ServiceItem item) {
        return Result.success("新增成功", serviceCatalogService.save(item));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid ServiceItem item) {
        item.setId(id);   // 以路径上的 id 为准，防止 body 与 url 不一致
        serviceCatalogService.update(item);
        return Result.success();
    }

    /** 上架/下架：status=1 上架，0 下架 */
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        serviceCatalogService.changeStatus(id, status);
        return Result.success(status == 1 ? "已上架" : "已下架", null);
    }
}
