package com.petcare.controller;

import com.petcare.common.PageResult;
import com.petcare.common.Result;
import com.petcare.entity.User;
import com.petcare.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台 - 用户管理（列表 + 启用/禁用）。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AuthService authService;

    @GetMapping
    public Result<PageResult<User>> page(@RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(authService.pageUsers(keyword, pageNum, pageSize));
    }

    /** status=1 启用，0 禁用（禁用会立刻踢下线） */
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        authService.changeStatus(id, status);
        return Result.success(status == 1 ? "已启用" : "已禁用", null);
    }
}
