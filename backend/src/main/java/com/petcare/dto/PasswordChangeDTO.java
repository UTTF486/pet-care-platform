package com.petcare.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 当前用户修改密码参数。
 */
@Data
public class PasswordChangeDTO {

    @NotBlank(message = "当前密码不能为空")
    @Size(min = 6, max = 20, message = "当前密码长度需为 6-20 位")
    private String currentPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度需为 6-20 位")
    private String newPassword;
}
