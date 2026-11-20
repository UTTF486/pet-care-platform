package com.petcare.vo;

import com.petcare.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录/注册成功返回：Token + 用户信息（密码已被 @JsonIgnore 过滤）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    private String token;
    private User user;
}
