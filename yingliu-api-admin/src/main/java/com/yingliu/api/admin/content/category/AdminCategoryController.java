package com.yingliu.api.admin.content.category;

import com.yingliu.api.admin.log.OperLog;
import com.yingliu.api.admin.content.shared.PageResponse;
import com.yingliu.api.admin.security.AdminPermissionAuthorizer;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.domain.content.category.ContentCategory;
import com.yingliu.service.content.category.ContentCategoryService;
import com.yingliu.service.content.shared.ServicePage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 内容分类管理入口，负责请求校验与统一响应转换。 */
@RestController
@Validated
@ConditionalOnBean(ContentCategoryService.class)
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryController {
    private final ContentCategoryService service;
    private final AdminPermissionAuthorizer authorizer;

    public AdminCategoryController(ContentCategoryService service, AdminPermissionAuthorizer authorizer) {
        this.service = service;
        this.authorizer = authorizer;
    }

    @GetMapping
    public ApiResponse<PageResponse<CategoryDtos.View>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        authorizer.require("category:view");
        ServicePage<ContentCategory> result = service.list(page, pageSize, keyword, status);
        return ApiResponse.ok(new PageResponse<>(result.items().stream().map(this::view).toList(), page, pageSize, result.total()), traceId(request));
    }

    /** Trash / recycle bin — list soft-deleted categories. */
    @GetMapping("/trash")
    public ApiResponse<PageResponse<CategoryDtos.View>> listTrash(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        authorizer.require("category:view");
        ServicePage<ContentCategory> result = service.listDeleted(page, pageSize, keyword);
        return ApiResponse.ok(new PageResponse<>(result.items().stream().map(this::view).toList(), page, pageSize, result.total()), traceId(request));
    }

    @PostMapping
    @OperLog(module = "TAXONOMY", action = "CREATE", targetType = "CATEGORY", detail = "创建分类")
    public ResponseEntity<ApiResponse<CategoryDtos.View>> create(@Valid @RequestBody CategoryDtos.Create body, HttpServletRequest request) {
        authorizer.require("category:manage");
        ContentCategory category = service.create(body.name(), body.icon(), body.sort(), body.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(view(category), traceId(request)));
    }

    @PatchMapping("/{categoryId}")
    @OperLog(module = "TAXONOMY", action = "UPDATE", targetType = "CATEGORY")
    public ApiResponse<CategoryDtos.View> update(@PathVariable long categoryId, @Valid @RequestBody CategoryDtos.Update body, HttpServletRequest request) {
        authorizer.require("category:manage");
        if (body.name() == null && body.icon() == null && body.sort() == null && body.status() == null) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "至少提供一个待更新字段");
        }
        return ApiResponse.ok(view(service.update(categoryId, body.name(), body.icon(), body.sort(), body.status())), traceId(request));
    }

    @DeleteMapping("/{categoryId}")
    @OperLog(module = "TAXONOMY", action = "DELETE", targetType = "CATEGORY")
    public ApiResponse<Void> delete(@PathVariable long categoryId, HttpServletRequest request) {
        authorizer.require("category:manage");
        service.delete(categoryId);
        return ApiResponse.ok(null, traceId(request));
    }

    /** Restore a soft-deleted category. */
    @PostMapping("/{categoryId}/restore")
    @OperLog(module = "TAXONOMY", action = "RESTORE", targetType = "CATEGORY", detail = "恢复分类")
    public ApiResponse<CategoryDtos.View> restore(@PathVariable long categoryId, HttpServletRequest request) {
        authorizer.require("category:manage");
        return ApiResponse.ok(view(service.restore(categoryId)), traceId(request));
    }

    private CategoryDtos.View view(ContentCategory category) {
        return new CategoryDtos.View(String.valueOf(category.getId()), category.getName(), category.getIcon(),
                category.getSort(), category.getStatus(),
                category.getDeleted() == null ? 0 : category.getDeleted(),
                category.getCreatedAt().toString(), category.getUpdatedAt().toString());
    }

    private String traceId(HttpServletRequest request) { return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE); }
}
