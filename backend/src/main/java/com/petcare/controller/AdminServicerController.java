package com.petcare.controller;

import com.petcare.common.PageResult;
import com.petcare.common.Result;
import com.petcare.entity.Servicer;
import com.petcare.service.ServicerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 后台 - 服务师信息维护。
 */
@RestController
@RequestMapping("/api/admin/servicers")
@RequiredArgsConstructor
public class AdminServicerController {

    private final ServicerService servicerService;

    @GetMapping
    public Result<PageResult<Servicer>> page(@RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(servicerService.page(keyword, pageNum, pageSize));
    }

    /** 接单弹窗用：当前可接单的服务师 */
    @GetMapping("/available")
    public Result<List<Servicer>> available() {
        return Result.success(servicerService.listAvailable());
    }

    @PostMapping
    public Result<Long> save(@RequestBody @Valid Servicer servicer) {
        return Result.success("新增成功", servicerService.save(servicer));
    }

    /**
     * 修改服务师。
     * 注意：这里故意不加 @Valid —— 前端切换"休息/可接单"时只传 status，
     * 若强校验 name 会直接 400。新增接口才需要完整校验。
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Servicer servicer) {
        servicer.setId(id);
        servicerService.update(servicer);
        return Result.success();
    }
}
