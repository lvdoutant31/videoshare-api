package com.yingliu.api.admin.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yingliu.api.admin.content.shared.PageResponse;
import com.yingliu.api.admin.security.AdminPermissionAuthorizer;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.domain.system.OperationLog;
import com.yingliu.service.system.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** 操作日志查询入口，日志写入由操作切面负责。 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/operation-logs")
public class AdminOperationLogController {

    private final OperationLogMapper logMapper;
    private final AdminPermissionAuthorizer authorizer;

    public AdminOperationLogController(OperationLogMapper logMapper, AdminPermissionAuthorizer authorizer) {
        this.logMapper = logMapper;
        this.authorizer = authorizer;
    }

    @GetMapping
    public ApiResponse<PageResponse<OperationLogDtos.Item>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            HttpServletRequest request) {
        authorizer.require("operation-log:view");

        Page<OperationLog> result = logMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<OperationLog>()
                        .eq(module != null && !module.isBlank(), OperationLog::getModule, module)
                        .eq(action != null && !action.isBlank(), OperationLog::getAction, action)
                        .eq(actorId != null, OperationLog::getActorId, actorId)
                        .eq(targetType != null && !targetType.isBlank(), OperationLog::getTargetType, targetType)
                        .eq(status != null && !status.isBlank(), OperationLog::getStatus, status)
                        .ge(startTime != null, OperationLog::getCreatedAt, startTime)
                        .le(endTime != null, OperationLog::getCreatedAt, endTime)
                        .orderByDesc(OperationLog::getCreatedAt));

        return ApiResponse.ok(new PageResponse<>(
                result.getRecords().stream().map(this::toItem).toList(),
                page, pageSize, result.getTotal()), traceId(request));
    }

    private OperationLogDtos.Item toItem(OperationLog log) {
        return new OperationLogDtos.Item(
                String.valueOf(log.getId()), log.getTraceId(),
                String.valueOf(log.getActorId()), log.getActorName(),
                log.getModule(), log.getAction(), log.getTargetType(), log.getTargetId(),
                log.getDetail(), log.getIp(),
                log.getRequestMethod(), log.getRequestPath(), log.getRequestParams(),
                log.getStatus(), log.getErrorCode(),
                log.getDurationMs() == null ? 0 : log.getDurationMs(),
                log.getCreatedAt() == null ? null : log.getCreatedAt().toString());
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
    }
}
