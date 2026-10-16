package com.yingliu.api.admin.content.tag;

import com.yingliu.api.admin.log.OperLog;
import com.yingliu.api.admin.content.shared.PageResponse;
import com.yingliu.api.admin.security.AdminPermissionAuthorizer;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.domain.content.tag.ContentTag;
import com.yingliu.service.content.shared.ServicePage;
import com.yingliu.service.content.tag.ContentTagService;
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

/** 内容标签管理入口，负责请求校验与统一响应转换。 */
@RestController
@Validated
@ConditionalOnBean(ContentTagService.class)
@RequestMapping("/api/v1/admin/tags")
public class AdminTagController {
    private final ContentTagService service;
    private final AdminPermissionAuthorizer authorizer;

    public AdminTagController(ContentTagService service, AdminPermissionAuthorizer authorizer) {
        this.service = service;
        this.authorizer = authorizer;
    }

    @GetMapping
    public ApiResponse<PageResponse<TagDtos.View>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        authorizer.require("tag:view");
        ServicePage<ContentTag> result = service.list(page, pageSize, keyword, status);
        return ApiResponse.ok(new PageResponse<>(result.items().stream().map(this::view).toList(), page, pageSize, result.total()), traceId(request));
    }

    /** Trash / recycle bin — list soft-deleted tags. */
    @GetMapping("/trash")
    public ApiResponse<PageResponse<TagDtos.View>> listTrash(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        authorizer.require("tag:view");
        ServicePage<ContentTag> result = service.listDeleted(page, pageSize, keyword);
        return ApiResponse.ok(new PageResponse<>(result.items().stream().map(this::view).toList(), page, pageSize, result.total()), traceId(request));
    }

    @PostMapping
    @OperLog(module = "TAXONOMY", action = "CREATE", targetType = "TAG", detail = "创建标签")
    public ResponseEntity<ApiResponse<TagDtos.View>> create(@Valid @RequestBody TagDtos.Create body, HttpServletRequest request) {
        authorizer.require("tag:create");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(view(service.create(body.name(), body.status(), body.heatScore())), traceId(request)));
    }

    @PatchMapping("/{tagId}")
    @OperLog(module = "TAXONOMY", action = "UPDATE", targetType = "TAG")
    public ApiResponse<TagDtos.View> update(@PathVariable long tagId, @Valid @RequestBody TagDtos.Update body, HttpServletRequest request) {
        authorizer.require("tag:update");
        if (body.name() == null && body.status() == null && body.heatScore() == null) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "至少提供一个待更新字段");
        }
        return ApiResponse.ok(view(service.update(tagId, body.name(), body.status(), body.heatScore())), traceId(request));
    }

    @DeleteMapping("/{tagId}")
    @OperLog(module = "TAXONOMY", action = "DELETE", targetType = "TAG")
    public ApiResponse<Void> delete(@PathVariable long tagId, HttpServletRequest request) {
        authorizer.require("tag:delete");
        service.delete(tagId);
        return ApiResponse.ok(null, traceId(request));
    }

    /** Restore a soft-deleted tag. */
    @PostMapping("/{tagId}/restore")
    @OperLog(module = "TAXONOMY", action = "RESTORE", targetType = "TAG", detail = "恢复标签")
    public ApiResponse<TagDtos.View> restore(@PathVariable long tagId, HttpServletRequest request) {
        authorizer.require("tag:restore");
        return ApiResponse.ok(view(service.restore(tagId)), traceId(request));
    }

    private TagDtos.View view(ContentTag tag) {
        return new TagDtos.View(String.valueOf(tag.getId()), tag.getName(), tag.getStatus(), tag.getHeatScore(),
                tag.getDeleted() == null ? 0 : tag.getDeleted(),
                tag.getCreatedAt().toString(), tag.getUpdatedAt().toString());
    }

    private String traceId(HttpServletRequest request) { return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE); }
}
