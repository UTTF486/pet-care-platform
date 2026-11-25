package com.petcare.dto;

import lombok.Data;

import javax.validation.constraints.FutureOrPresent;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 下单参数。
 * 只让前端传"选了哪只宠物、哪个服务、什么时候上门、上门地址"，
 * 金额、状态、订单号一律后端生成，防止前端篡改价格。
 */
@Data
public class OrderCreateDTO {

    /** 客户端生成的幂等请求号；同一次提交重试必须复用，成功后前端再生成新值。 */
    @NotBlank(message = "请求号不能为空")
    @Size(min = 8, max = 64, message = "请求号长度需为 8-64 位")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "请求号格式不正确")
    private String requestId;

    @NotNull(message = "请选择宠物")
    private Long petId;

    @NotNull(message = "请选择服务项目")
    private Long serviceId;

    @NotNull(message = "请选择预约日期")
    @FutureOrPresent(message = "预约日期不能早于今天")
    private LocalDate appointDate;

    @NotBlank(message = "请选择预约时段")
    @Size(max = 32, message = "预约时段最多 32 个字符")
    private String timeSlot;

    @NotBlank(message = "联系人不能为空")
    @Size(max = 32, message = "联系人最多 32 个字符")
    private String contactName;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String contactPhone;

    @NotBlank(message = "上门地址不能为空")
    @Size(max = 255, message = "上门地址最多 255 个字符")
    private String address;

    @Size(max = 255, message = "备注最多 255 个字符")
    private String remark;
}
