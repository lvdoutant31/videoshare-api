package com.yingliu.common.api;

/** 统一响应信封。code 为稳定业务码，traceId 用于关联 HTTP 响应与服务端日志。 */
public record ApiResponse<T>(String code, String message, T data, String traceId) {
    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>("OK", "", data, traceId);
    }

    public static ApiResponse<Void> fail(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId);
    }
}
