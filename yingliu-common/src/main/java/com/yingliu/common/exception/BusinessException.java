package com.yingliu.common.exception;

import org.springframework.http.HttpStatus;

/** 可向调用方展示的业务异常；保存既有业务码与 HTTP 状态，由 framework 统一映射响应。 */
public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
