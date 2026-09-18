package com.yingliu.api.admin.content.video;

import com.yingliu.service.content.video.VideoUploadService;
import com.yingliu.api.admin.content.shared.PageResponse;
import com.yingliu.api.admin.log.OperLog;
import com.yingliu.api.admin.security.AdminPermissionAuthorizer;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.content.shared.ServicePage;
import com.yingliu.service.content.video.VideoAdminService;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** 视频管理 HTTP 入口：解析管理端身份、校验请求并委托视频业务用例。 */
@RestController
@Validated
@ConditionalOnBean(VideoAdminService.class)
@RequestMapping("/api/v1/admin/videos")
public class AdminVideoController {
    private final VideoAdminService service;
    private final AdminPermissionAuthorizer authorizer;
    private final VideoUploadService uploadService;

    public AdminVideoController(VideoAdminService service, AdminPermissionAuthorizer authorizer, VideoUploadService uploadService) {
        this.service = service;
        this.authorizer = authorizer;
        this.uploadService = uploadService;
    }

    @GetMapping
    public ApiResponse<PageResponse<VideoDtos.Summary>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) String auditStatus,
            @RequestParam(required = false) String publishStatus,
            HttpServletRequest request) {
        authorizer.require("video:view");
        ServicePage<VideoAdminService.VideoSummary> result = service.list(
                new VideoAdminService.VideoQuery(page, pageSize, keyword, authorId, auditStatus, publishStatus));
        return ApiResponse.ok(new PageResponse<>(result.items().stream().map(this::summary).toList(), page, pageSize,
                result.total()), traceId(request));
    }

    /** POST /api/v1/admin/videos/upload：multipart 上传文件并一步创建视频（区别于下方 JSON 创建）。 */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @OperLog(module = "VIDEO", action = "CREATE", targetType = "VIDEO", detail = "上传文件并创建视频")
    public ResponseEntity<ApiResponse<VideoDtos.Detail>> upload(
            @RequestPart MultipartFile file, @RequestParam String title,
            @RequestParam(required = false) String description, @RequestParam(required = false) String sourceNote,
            @RequestParam(required = false, defaultValue = "") List<String> categoryIds,
            @RequestParam(required = false, defaultValue = "") List<String> tagIds,
            @RequestParam(required = false, defaultValue = "PUBLIC") String visibility, HttpServletRequest request) {
        long adminId = authorizer.require("video:create");
        var detail = uploadService.uploadAndCreate(adminId, service.currentAuthorId(adminId), file, title, description,
                sourceNote, ids(categoryIds), ids(tagIds), visibility);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(detail(detail), traceId(request)));
    }

    /** POST /api/v1/admin/videos：JSON 创建视频（引用已存在的媒体文件）。 */
    @PostMapping(consumes = "application/json")
    @OperLog(module = "VIDEO", action = "CREATE", targetType = "VIDEO", detail = "创建视频草稿")
    public ResponseEntity<ApiResponse<VideoDtos.Detail>> create(@Valid @RequestBody VideoDtos.CreateDraft body,
                                                                  HttpServletRequest request) {
        long adminId = authorizer.require("video:create");
        VideoAdminService.CreateDraftCommand command = new VideoAdminService.CreateDraftCommand(
                adminId, service.currentAuthorId(adminId), id(body.originalMediaId()), optionalId(body.coverMediaId()),
                body.title(), body.description(), body.sourceNote(), ids(body.categoryIds()), ids(body.tagIds()), body.visibility());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(detail(service.createDraft(command)), traceId(request)));
    }

    @GetMapping("/{videoId}")
    public ApiResponse<VideoDtos.Detail> get(@PathVariable long videoId, HttpServletRequest request) {
        authorizer.require("video:view");
        return ApiResponse.ok(detail(service.get(videoId)), traceId(request));
    }

    /** PATCH /api/v1/admin/videos/{videoId}：JSON 更新视频元数据。 */
    @PatchMapping(value = "/{videoId}", consumes = "application/json")
    @OperLog(module = "VIDEO", action = "UPDATE", targetType = "VIDEO")
    public ApiResponse<VideoDtos.Detail> update(@PathVariable long videoId, @Valid @RequestBody VideoDtos.Update body, HttpServletRequest request) {
        return ApiResponse.ok(detail(service.update(videoId, authorizer.require("video:update"), new VideoAdminService.UpdateCommand(body.title(), body.description(), body.visibility(), ids(body.categoryIds()), ids(body.tagIds())))), traceId(request));
    }



    /** PATCH /api/v1/admin/videos/{videoId}/file：multipart 上传新文件替换视频（区别于上方 JSON 元数据更新）。 */
    @PatchMapping(value = "/{videoId}/file", consumes = "multipart/form-data")
    @OperLog(module = "VIDEO", action = "UPDATE", targetType = "VIDEO", detail = "上传新文件替换视频")
    public ApiResponse<VideoDtos.Detail> replaceFile(
            @PathVariable long videoId, @RequestPart MultipartFile file, @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam String visibility,
            @RequestParam(required = false, defaultValue = "") List<String> categoryIds,
            @RequestParam(required = false, defaultValue = "") List<String> tagIds,
            HttpServletRequest request) {
        return ApiResponse.ok(detail(uploadService.uploadAndReplace(videoId, authorizer.require("video:update"),
                file, title, description, ids(categoryIds), ids(tagIds), visibility)), traceId(request));
    }

    @PostMapping("/{videoId}/submit-review")
    @OperLog(module = "VIDEO", action = "SUBMIT", targetType = "VIDEO", detail = "提交审核")
    public ApiResponse<VideoDtos.Detail> submitForReview(@PathVariable long videoId, HttpServletRequest request) {
        return ApiResponse.ok(detail(service.submitForReview(videoId, authorizer.require("video:submit-review"))), traceId(request));
    }

    @PostMapping("/{videoId}/publish")
    @OperLog(module = "VIDEO", action = "PUBLISH", targetType = "VIDEO", detail = "立即发布")
    public ApiResponse<VideoDtos.Detail> publish(@PathVariable long videoId, HttpServletRequest request) {
        return ApiResponse.ok(detail(service.publishNow(videoId, authorizer.require("video:publish"))), traceId(request));
    }

    @PostMapping("/{videoId}/schedule-publish")
    @OperLog(module = "VIDEO", action = "SCHEDULE", targetType = "VIDEO", detail = "定时发布")
    public ApiResponse<VideoDtos.Detail> schedulePublish(@PathVariable long videoId,
                                                           @Valid @RequestBody VideoDtos.SchedulePublish body,
                                                           HttpServletRequest request) {
        return ApiResponse.ok(detail(service.schedulePublish(videoId, authorizer.require("video:publish"), body.scheduledAt())),
                traceId(request));
    }

    @PostMapping("/{videoId}/cancel-scheduled-publish")
    @OperLog(module = "VIDEO", action = "CANCEL", targetType = "VIDEO", detail = "取消定时发布")
    public ApiResponse<VideoDtos.Detail> cancelScheduledPublish(@PathVariable long videoId, HttpServletRequest request) {
        return ApiResponse.ok(detail(service.cancelScheduledPublish(videoId, authorizer.require("video:publish"))), traceId(request));
    }

    @PostMapping("/batch/off-shelf")
    @OperLog(module = "VIDEO", action = "BATCH_OFF_SHELF", targetType = "VIDEO", detail = "批量下架")
    public ApiResponse<VideoAdminService.BatchOperationResult> batchOffShelf(@Valid @RequestBody VideoDtos.BatchVideoIds body, HttpServletRequest request) {
        return ApiResponse.ok(service.batchOffShelf(ids(body.videoIds()), authorizer.require("video:off-shelf"), body.reason()), traceId(request));
    }

    @PostMapping("/batch/restore")
    @OperLog(module = "VIDEO", action = "BATCH_RESTORE", targetType = "VIDEO", detail = "批量恢复")
    public ApiResponse<VideoAdminService.BatchOperationResult> batchRestore(@Valid @RequestBody VideoDtos.BatchVideoIds body, HttpServletRequest request) {
        return ApiResponse.ok(service.batchRestore(ids(body.videoIds()), authorizer.require("video:off-shelf")), traceId(request));
    }

    @PostMapping("/batch/delete")
    @OperLog(module = "VIDEO", action = "BATCH_DELETE", targetType = "VIDEO", detail = "批量删除")
    public ApiResponse<VideoAdminService.BatchOperationResult> batchDelete(@Valid @RequestBody VideoDtos.BatchVideoIds body, HttpServletRequest request) {
        return ApiResponse.ok(service.batchDelete(ids(body.videoIds()), authorizer.require("video:delete"), body.reason()), traceId(request));
    }

    @PostMapping("/{videoId}/transcodes/{profile}/retry")
    @OperLog(module = "VIDEO", action = "RETRY", targetType = "VIDEO", detail = "重试转码")
    public ApiResponse<Void> retryTranscode(@PathVariable long videoId, @PathVariable String profile, HttpServletRequest request) {
        service.retryTranscode(videoId, profile, authorizer.require("video:transcode:retry"));
        return ApiResponse.ok(null, traceId(request));
    }

    @PostMapping("/{videoId}/off-shelf")
    @OperLog(module = "VIDEO", action = "OFF_SHELF", targetType = "VIDEO", detail = "下架")
    public ApiResponse<VideoDtos.Detail> offShelf(@PathVariable long videoId, @Valid @RequestBody VideoDtos.Reason body,
                                                    HttpServletRequest request) {
        return ApiResponse.ok(detail(service.offShelf(videoId, authorizer.require("video:off-shelf"), body.reason())), traceId(request));
    }

    @PostMapping("/{videoId}/restore")
    @OperLog(module = "VIDEO", action = "RESTORE", targetType = "VIDEO", detail = "恢复")
    public ApiResponse<VideoDtos.Detail> restore(@PathVariable long videoId, HttpServletRequest request) {
        return ApiResponse.ok(detail(service.restore(videoId, authorizer.require("video:off-shelf"))), traceId(request));
    }

    @DeleteMapping("/{videoId}")
    @OperLog(module = "VIDEO", action = "DELETE", targetType = "VIDEO")
    public ApiResponse<Void> delete(@PathVariable long videoId, @Valid @RequestBody VideoDtos.Reason body,
                                    HttpServletRequest request) {
        service.delete(videoId, authorizer.require("video:delete"), body.reason());
        return ApiResponse.ok(null, traceId(request));
    }

    private VideoDtos.Summary summary(VideoAdminService.VideoSummary item) {
        return new VideoDtos.Summary(String.valueOf(item.id()), String.valueOf(item.authorId()), item.authorNickname(),
                item.title(), item.auditStatus(), item.publishStatus(), item.visibility(), time(item.createdAt()), time(item.updatedAt()));
    }

    private VideoDtos.Detail detail(VideoAdminService.VideoDetail item) {
        return new VideoDtos.Detail(String.valueOf(item.id()), String.valueOf(item.authorId()), item.authorNickname(),
                item.title(), item.description(), item.sourceNote(), media(item.originalMedia()), media(item.coverMedia()),
                item.auditStatus(), item.publishStatus(), item.visibility(), item.riskLevel(), item.currentVersion(),
                item.categories().stream().map(category -> new VideoDtos.CategoryView(String.valueOf(category.id()), category.name())).toList(),
                item.tags().stream().map(tag -> new VideoDtos.TagView(String.valueOf(tag.id()), tag.name())).toList(),
                time(item.scheduledAt()), time(item.publishedAt()), time(item.createdAt()), time(item.updatedAt()));
    }

    private VideoDtos.MediaView media(VideoAdminService.MediaView item) {
        if (item == null) return null;
        return new VideoDtos.MediaView(String.valueOf(item.id()), item.resourceType(), item.contentType(), item.fileSize(),
                item.width(), item.height(), item.durationMs(), item.url());
    }

    private List<Long> ids(List<String> values) {
        if (values == null) return List.of();
        return values.stream().map(this::id).toList();
    }

    private Long optionalId(String value) {
        return value == null || value.isBlank() ? null : id(value);
    }

    private long id(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw validation();
            return id;
        } catch (NumberFormatException exception) {
            throw validation();
        }
    }

    private BusinessException validation() {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "ID 格式不合法");
    }

    private String time(LocalDateTime value) { return value == null ? null : value.toString(); }
    private String traceId(HttpServletRequest request) { return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE); }
}
