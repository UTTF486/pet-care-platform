package com.petcare.controller;

import com.petcare.common.Result;
import com.petcare.common.UserContext;
import com.petcare.dto.AccountDTO;
import com.petcare.entity.User;
import com.petcare.service.AuthService;
import com.petcare.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 认证相关接口。
 * 登录、注册两个接口在 WebConfig 里被排除在拦截器之外。
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
}
