package com.petcare.common;

import lombok.Getter;

/**
 * 业务异常。
 * 业务代码里 throw new BusinessException("订单状态已变更")，
 * 由 GlobalExceptionHandler 统一转成 Result，不用每个接口写 try-catch。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.PARAM_ERROR.getCode();
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
