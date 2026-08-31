package com.yingliu.common.api;

public record ApiResponse<T>(String code, String message, T data, String traceId) {
    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>("OK", "", data, traceId);
    }

    public static ApiResponse<Void> fail(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId);
    }
}
