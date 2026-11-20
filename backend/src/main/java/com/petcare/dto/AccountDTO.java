package com.petcare.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 注册/登录参数。两个接口共用，注册时多校验昵称。
 */
@Data
public class AccountDTO {

    @NotBlank(message = "账号不能为空")
    @Size(min = 3, max = 20, message = "账号长度需为 3-20 位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需为 6-20 位")
    private String password;

    /** 注册时使用 */
    private String nickname;
    private String phone;
}
