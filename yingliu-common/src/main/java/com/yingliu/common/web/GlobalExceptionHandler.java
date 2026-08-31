package com.yingliu.common.web;

import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception, HttpServletRequest request) {
        return response(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidation(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "请求参数不合法", request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "名称已存在", request);
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, String code, String message, HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
        return ResponseEntity.status(status).body(ApiResponse.fail(code, message, traceId));
    }
}
