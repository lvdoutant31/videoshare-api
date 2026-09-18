package com.yingliu.api.admin.dashboard;

import com.yingliu.api.admin.security.AdminPermissionAuthorizer;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.dashboard.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/** 管理端概览数据入口，统一包装看板查询结果。 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {
    private final DashboardService service;
    private final AdminPermissionAuthorizer authorizer;
    public AdminDashboardController(DashboardService service, AdminPermissionAuthorizer authorizer) { this.service=service; this.authorizer=authorizer; }
    @GetMapping("/summary") public ApiResponse<DashboardDtos.Summary> summary(HttpServletRequest r) { authorizer.require("dashboard:view"); return ApiResponse.ok(DashboardDtos.summary(service.summary()), trace(r)); }
    @GetMapping("/trends") public ApiResponse<DashboardDtos.Trend> trends(@RequestParam(defaultValue="7d") String range,HttpServletRequest r) { authorizer.require("dashboard:view"); return ApiResponse.ok(DashboardDtos.trend(service.trend(range)), trace(r)); }
    @GetMapping("/todos") public ApiResponse<DashboardDtos.Todos> todos(HttpServletRequest r) { authorizer.require("dashboard:view"); return ApiResponse.ok(new DashboardDtos.Todos(service.todos().stream().map(DashboardDtos::todo).toList()), trace(r)); }
    @GetMapping("/storage") public ApiResponse<DashboardDtos.Storage> storage(HttpServletRequest r) { authorizer.require("storage:view"); return ApiResponse.ok(DashboardDtos.storage(service.storage()), trace(r)); }
    private String trace(HttpServletRequest r){return (String)r.getAttribute(TraceIdFilter.ATTRIBUTE);}
}
