package com.petcare.controller;

import com.petcare.common.PageResult;
import com.petcare.common.Result;
import com.petcare.entity.Category;
import com.petcare.entity.ServiceItem;
import com.petcare.service.ServiceCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端服务浏览接口。
 * 这几个 GET 接口允许游客访问（拦截器白名单），未登录也能先逛再决定注册。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceCatalogService serviceCatalogService;

    /** 服务品类，首页顶部分类标签 */
    @GetMapping("/categories")
    public Result<List<Category>> categories() {
        return Result.success(serviceCatalogService.listCategories());
    }

    /** 热门服务（Redis 缓存） */
    @GetMapping("/services/hot")
    public Result<List<ServiceItem>> hot() {
        return Result.success(serviceCatalogService.listHot());
    }

    /** 服务列表，支持品类筛选 + 关键字搜索 + 分页 */
    @GetMapping("/services")
    public Result<PageResult<ServiceItem>> list(@RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "8") int pageSize) {
        return Result.success(serviceCatalogService.pageForUser(categoryId, keyword, pageNum, pageSize));
    }

    /** 服务详情（Redis 缓存） */
    @GetMapping("/services/{id}")
    public Result<ServiceItem> detail(@PathVariable Long id) {
        return Result.success(serviceCatalogService.detail(id));
    }
}
