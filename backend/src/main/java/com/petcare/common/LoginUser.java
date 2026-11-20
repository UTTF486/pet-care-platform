package com.petcare.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录用户上下文对象（精简版，只放鉴权要用的字段）。
 * 序列化成 JSON 存 Redis，避免每次请求都查数据库。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    private Long id;
    private String username;
    private String nickname;
    /** 0-普通用户 1-商家管理员 */
    private Integer role;

    public boolean isAdmin() {
        return role != null && role == 1;
    }
}
