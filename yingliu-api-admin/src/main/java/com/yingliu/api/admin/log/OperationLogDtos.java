package com.yingliu.api.admin.log;

import java.time.LocalDateTime;

public final class OperationLogDtos {
    private OperationLogDtos() { }

    public record Item(String id, String traceId, String actorId, String actorName,
                       String module, String action, String targetType, String targetId,
                       String detail, String ip, String requestMethod, String requestPath,
                       String requestParams, String status, String errorCode,
                       int durationMs, String createdAt) { }

    public record Query(long page, long pageSize, String module, String action,
                        Long actorId, String targetType, String status,
                        LocalDateTime startTime, LocalDateTime endTime) { }
}
