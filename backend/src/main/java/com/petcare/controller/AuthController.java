package com.petcare.controller;

import com.petcare.common.Result;
import com.petcare.common.UserContext;
import com.petcare.dto.AccountDTO;
import com.petcare.dto.PasswordChangeDTO;
import com.petcare.entity.User;
import com.petcare.service.AuthService;
import com.petcare.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 认证相关接口。
 * 登录、注册由 AuthInterceptor 的公开规则放行，其余接口需要 Token。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册（成功后直接返回 token，前端可免登录） */
    @PostMapping("/register")
    public Result<LoginVO> register(@RequestBody @Valid AccountDTO dto) {
        return Result.success("注册成功", authService.register(dto));
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid AccountDTO dto) {
        return Result.success("登录成功", authService.login(dto));
    }

    /** 退出登录：删掉 Redis 里的 token */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null) {
            String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
            authService.logout(token);
        }
        return Result.success();
    }

    /** 当前登录用户信息，前端刷新页面后用它恢复状态 */
    @GetMapping("/profile")
    public Result<User> profile() {
        return Result.success(authService.getProfile(UserContext.getUserId()));
    }

    /** 当前用户修改密码；成功后所有旧 Token 失效。 */
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody @Valid PasswordChangeDTO dto) {
        authService.changePassword(UserContext.getUserId(), dto);
        return Result.success("密码已修改，请重新登录", null);
    }
}
