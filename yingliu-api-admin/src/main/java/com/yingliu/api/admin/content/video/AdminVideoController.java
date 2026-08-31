package com.yingliu.api.admin.content.video;

import com.yingliu.api.admin.content.shared.PageResponse;
import com.yingliu.api.admin.security.AdminActorProvider;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.common.web.TraceIdFilter;
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

@RestController
@Validated
@ConditionalOnBean(VideoAdminService.class)
@RequestMapping("/api/v1/admin/videos")
public class AdminVideoController {
    private final VideoAdminService service;
    private final AdminActorProvider actorProvider;
    private final VideoUploadService uploadService;

    public AdminVideoController(VideoAdminService service, AdminActorProvider actorProvider, VideoUploadService uploadService) {
        this.service = service;
        this.actorProvider = actorProvider;
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
        ServicePage<VideoAdminService.VideoSummary> result = service.list(
                new VideoAdminService.VideoQuery(page, pageSize, keyword, authorId, auditStatus, publishStatus));
        return ApiResponse.ok(new PageResponse<>(result.items().stream().map(this::summary).toList(), page, pageSize,
                result.total()), traceId(request));
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<VideoDtos.Detail>> uploadCreate(
            @RequestParam long authorId, @RequestPart MultipartFile file, @RequestParam String title,
            @RequestParam(required = false) String description, @RequestParam(required = false) String sourceNote,
            @RequestParam(required = false, defaultValue = "") List<String> categoryIds,
            @RequestParam(required = false, defaultValue = "") List<String> tagIds,
            @RequestParam(required = false, defaultValue = "PUBLIC") String visibility, HttpServletRequest request) {
        var detail = uploadService.uploadAndCreate(actorProvider.currentAdminId(), authorId, file, title, description,
                sourceNote, ids(categoryIds), ids(tagIds), visibility);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(detail(detail), traceId(request)));
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<ApiResponse<VideoDtos.Detail>> create(@Valid @RequestBody VideoDtos.CreateDraft body,
                                                                  HttpServletRequest request) {
        VideoAdminService.CreateDraftCommand command = new VideoAdminService.CreateDraftCommand(
                actorProvider.currentAdminId(), id(body.authorId()), id(body.originalMediaId()), optionalId(body.coverMediaId()),
                body.title(), body.description(), body.sourceNote(), ids(body.categoryIds()), ids(body.tagIds()), body.visibility());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(detail(service.createDraft(command)), traceId(request)));
    }

    @GetMapping("/{videoId}")
    public ApiResponse<VideoDtos.Detail> get(@PathVariable long videoId, HttpServletRequest request) {
        return ApiResponse.ok(detail(service.get(videoId)), traceId(request));
    }

    @PatchMapping("/{videoId}")
    public ApiResponse<VideoDtos.Detail> update(@PathVariable long videoId, @Valid @RequestBody VideoDtos.Update body, HttpServletRequest request) {
        return ApiResponse.ok(detail(service.update(videoId, actorProvider.currentAdminId(), new VideoAdminService.UpdateCommand(body.title(), body.description(), body.visibility(), ids(body.categoryIds()), ids(body.tagIds())))), traceId(request));
    }


    @PostMapping("/{videoId}/submit-review")
    public ApiResponse<VideoDtos.Detail> submitForReview(@PathVariable long videoId, HttpServletRequest request) {
        return ApiResponse.ok(detail(service.submitForReview(videoId, actorProvider.currentAdminId())), traceId(request));
    }

    @PostMapping("/{videoId}/publish")
    public ApiResponse<VideoDtos.Detail> publish(@PathVariable long videoId, HttpServletRequest request) {
        return ApiResponse.ok(detail(service.publishNow(videoId, actorProvider.currentAdminId())), traceId(request));
    }

    @PostMapping("/{videoId}/schedule-publish")
    public ApiResponse<VideoDtos.Detail> schedulePublish(@PathVariable long videoId,
                                                           @Valid @RequestBody VideoDtos.SchedulePublish body,
                                                           HttpServletRequest request) {
        return ApiResponse.ok(detail(service.schedulePublish(videoId, actorProvider.currentAdminId(), body.scheduledAt())),
                traceId(request));
    }

    @PostMapping("/{videoId}/cancel-scheduled-publish")
    public ApiResponse<VideoDtos.Detail> cancelScheduledPublish(@PathVariable long videoId, HttpServletRequest request) {
        return ApiResponse.ok(detail(service.cancelScheduledPublish(videoId, actorProvider.currentAdminId())), traceId(request));
    }

    @PostMapping("/batch/off-shelf")
    public ApiResponse<VideoAdminService.BatchOperationResult> batchOffShelf(@Valid @RequestBody VideoDtos.BatchVideoIds body, HttpServletRequest request) {
        return ApiResponse.ok(service.batchOffShelf(ids(body.videoIds()), actorProvider.currentAdminId(), body.reason()), traceId(request));
    }

    @PostMapping("/batch/restore")
    public ApiResponse<VideoAdminService.BatchOperationResult> batchRestore(@Valid @RequestBody VideoDtos.BatchVideoIds body, HttpServletRequest request) {
        return ApiResponse.ok(service.batchRestore(ids(body.videoIds()), actorProvider.currentAdminId()), traceId(request));
    }

    @PostMapping("/{videoId}/transcodes/{profile}/retry")
    public ApiResponse<Void> retryTranscode(@PathVariable long videoId, @PathVariable String profile, HttpServletRequest request) {
        service.retryTranscode(videoId, profile, actorProvider.currentAdminId());
        return ApiResponse.ok(null, traceId(request));
    }

    @PostMapping("/{videoId}/off-shelf")
    public ApiResponse<VideoDtos.Detail> offShelf(@PathVariable long videoId, @Valid @RequestBody VideoDtos.Reason body,
                                                    HttpServletRequest request) {
        return ApiResponse.ok(detail(service.offShelf(videoId, actorProvider.currentAdminId(), body.reason())), traceId(request));
    }

    @PostMapping("/{videoId}/restore")
    public ApiResponse<VideoDtos.Detail> restore(@PathVariable long videoId, HttpServletRequest request) {
        return ApiResponse.ok(detail(service.restore(videoId, actorProvider.currentAdminId())), traceId(request));
    }

    @DeleteMapping("/{videoId}")
    public ApiResponse<Void> delete(@PathVariable long videoId, @Valid @RequestBody VideoDtos.Reason body,
                                    HttpServletRequest request) {
        service.delete(videoId, actorProvider.currentAdminId(), body.reason());
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
                item.width(), item.height(), item.durationMs());
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
