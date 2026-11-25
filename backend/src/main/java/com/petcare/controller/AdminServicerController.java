package com.petcare.controller;

import com.petcare.common.PageResult;
import com.petcare.common.Result;
import com.petcare.entity.Servicer;
import com.petcare.service.ServicerService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
    public Result<Long> save(@RequestBody @Validated(Servicer.Create.class) Servicer servicer) {
        return Result.success("新增成功", servicerService.save(servicer));
    }

    /**
     * 修改服务师。Update 分组允许只传 status 等单个字段。
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @RequestBody @Validated(Servicer.Update.class) Servicer servicer) {
        servicer.setId(id);
        servicerService.update(servicer);
        return Result.success();
    }
}
