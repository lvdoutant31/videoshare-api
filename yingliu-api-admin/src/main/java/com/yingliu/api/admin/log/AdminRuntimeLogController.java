package com.yingliu.api.admin.log;

import com.yingliu.api.admin.security.AdminPermissionAuthorizer;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端运行日志只读查询。 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/runtime-logs")
public class AdminRuntimeLogController {
    private final RuntimeLogReader logReader;
    private final AdminPermissionAuthorizer authorizer;

    public AdminRuntimeLogController(RuntimeLogReader logReader, AdminPermissionAuthorizer authorizer) {
        this.logReader = logReader;
        this.authorizer = authorizer;
    }

    @GetMapping
    public ApiResponse<RuntimeLogReader.Snapshot> tail(
            @RequestParam(defaultValue = "300") @Min(1) @Max(1000) int lines,
            HttpServletRequest request) throws java.io.IOException {
        authorizer.require("operation-log:view");
        return ApiResponse.ok(logReader.tail(lines), (String) request.getAttribute(TraceIdFilter.ATTRIBUTE));
    }
}
