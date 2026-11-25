package com.petcare.common;

import lombok.Getter;

/**
 * 业务状态码枚举。
 * 用枚举而不是散落的魔法数字，前端也能按 code 做统一处理（如 401 跳登录）。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数不合法"),
    UNAUTHORIZED(401, "登录已失效，请重新登录"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "数据不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    CONFLICT(409, "数据冲突，请刷新后重试"),
    RATE_LIMIT(429, "操作太频繁了，请稍后再试"),
    ERROR(500, "系统开小差了，请稍后再试");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
