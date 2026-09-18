package com.yingliu.framework.web;

import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 统一转换 Web 异常为业务响应，未知异常仅记录服务端日志，不返回内部实现细节。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception, HttpServletRequest request) {
        return response(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException exception,
                                                               HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "视频文件超过服务器允许的大小", request);
    }

    // 参数校验失败属于调用方输入问题，不能落入未知异常的 500 分支。
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidation(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "请求参数不合法", request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "名称已存在", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingResource(NoResourceFoundException exception,
                                                                     HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "资源不存在", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
        log.error("Unhandled server exception: method={}, uri={}, traceId={}",
                request.getMethod(), request.getRequestURI(), traceId, exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "系统繁忙，请稍后再试", request);
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, String code, String message, HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
        return ResponseEntity.status(status).body(ApiResponse.fail(code, message, traceId));
    }
}
