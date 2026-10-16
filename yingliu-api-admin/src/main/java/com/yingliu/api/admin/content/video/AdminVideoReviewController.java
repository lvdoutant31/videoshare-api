package com.yingliu.api.admin.content.video;

import com.yingliu.api.admin.log.OperLog;
import com.yingliu.api.admin.content.shared.PageResponse;
import com.yingliu.api.admin.security.AdminPermissionAuthorizer;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.content.shared.ServicePage;
import com.yingliu.service.content.video.VideoAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** 视频审核 HTTP 入口：将审核版本、处理人和意见传递给审核业务。 */
@RestController
@Validated
@ConditionalOnBean(VideoAdminService.class)
@RequestMapping("/api/v1/admin/reviews/videos")
public class AdminVideoReviewController {
    private final VideoAdminService service;
    private final AdminPermissionAuthorizer authorizer;

    public AdminVideoReviewController(VideoAdminService service, AdminPermissionAuthorizer authorizer) {
        this.service = service;
        this.authorizer = authorizer;
    }

    @GetMapping
    public ApiResponse<PageResponse<VideoDtos.ReviewQueueItem>> queue(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) String riskLevel,
            HttpServletRequest request) {
        long actorAdminId = authorizer.require("review:view");
        ServicePage<VideoAdminService.ReviewQueueItem> result = service.listReviewQueue(
                new VideoAdminService.ReviewQueueQuery(page, pageSize, keyword, authorId, riskLevel, actorAdminId));
        return ApiResponse.ok(new PageResponse<>(result.items().stream().map(this::queueItem).toList(), page, pageSize,
                result.total()), traceId(request));
    }

    @GetMapping("/{videoId}")
    public ApiResponse<VideoDtos.Detail> get(@PathVariable long videoId, HttpServletRequest request) {
        authorizer.require("review:view");
        return ApiResponse.ok(detail(service.get(videoId)), traceId(request));
    }

    @PostMapping("/{videoId}/claim")
    @OperLog(module = "REVIEW", action = "CLAIM", targetType = "VIDEO", detail = "认领审核")
    public ApiResponse<VideoDtos.Detail> claim(@PathVariable long videoId, @Valid @RequestBody VideoDtos.Claim body, HttpServletRequest request) {
        return ApiResponse.ok(detail(service.claimReview(videoId, authorizer.require("review:claim"), body.version())), traceId(request));
    }

    @PostMapping("/{videoId}/release")
    @OperLog(module = "REVIEW", action = "RELEASE", targetType = "VIDEO", detail = "释放审核")
    public ApiResponse<VideoDtos.Detail> release(@PathVariable long videoId, @Valid @RequestBody VideoDtos.Claim body, HttpServletRequest request) {
        return ApiResponse.ok(detail(service.releaseReview(videoId, authorizer.require("review:claim"), body.version())), traceId(request));
    }

    @PostMapping("/{videoId}/request-revision")
    @OperLog(module = "REVIEW", action = "REQUEST_REVISION", targetType = "VIDEO", detail = "要求修改")
    public ApiResponse<VideoDtos.Detail> requestRevision(@PathVariable long videoId, @Valid @RequestBody VideoDtos.RequestRevision body, HttpServletRequest request) {
        return ApiResponse.ok(detail(service.requestRevision(videoId, authorizer.require("review:decide"), body.currentVersion(), body.reason())), traceId(request));
    }


    @PostMapping("/{videoId}/approve")
    @OperLog(module = "REVIEW", action = "APPROVE", targetType = "VIDEO", detail = "审核通过")
    public ApiResponse<VideoDtos.Detail> approve(@PathVariable long videoId, @Valid @RequestBody VideoDtos.ApproveReview body,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(detail(service.approveReview(videoId, authorizer.require("review:decide"), body.currentVersion(),
                body.reason())), traceId(request));
    }

    @PostMapping("/{videoId}/reject")
    @OperLog(module = "REVIEW", action = "REJECT", targetType = "VIDEO", detail = "审核驳回")
    public ApiResponse<VideoDtos.Detail> reject(@PathVariable long videoId, @Valid @RequestBody VideoDtos.RejectReview body,
                                                  HttpServletRequest request) {
        return ApiResponse.ok(detail(service.rejectReview(videoId, authorizer.require("review:decide"), body.currentVersion(),
                body.reason())), traceId(request));
    }

    @GetMapping("/{videoId}/history")
    public ApiResponse<List<VideoDtos.AuditHistoryItem>> history(@PathVariable long videoId, HttpServletRequest request) {
        authorizer.require("review:view");
        return ApiResponse.ok(service.auditHistory(videoId).stream().map(this::historyItem).toList(), traceId(request));
    }

    private VideoDtos.ReviewQueueItem queueItem(VideoAdminService.ReviewQueueItem item) {
        return new VideoDtos.ReviewQueueItem(String.valueOf(item.id()), String.valueOf(item.authorId()), item.authorNickname(),
                item.title(), item.riskLevel(), item.currentVersion(), time(item.submittedAt()));
    }

    private VideoDtos.AuditHistoryItem historyItem(VideoAdminService.AuditHistoryItem item) {
        return new VideoDtos.AuditHistoryItem(String.valueOf(item.id()), item.videoVersion(),
                item.reviewerAdminId() == null ? null : String.valueOf(item.reviewerAdminId()), item.action(), item.reason(),
                time(item.claimedAt()), time(item.reviewedAt()), time(item.createdAt()));
    }

    private VideoDtos.Detail detail(VideoAdminService.VideoDetail item) {
        return new VideoDtos.Detail(String.valueOf(item.id()), String.valueOf(item.authorId()), item.authorNickname(),
                item.title(), item.description(), item.sourceNote(), media(item.originalMedia()), media(item.coverMedia()),
                item.auditStatus(), item.publishStatus(), item.visibility(), item.riskLevel(), item.currentVersion(),
                item.categories().stream().map(category -> new VideoDtos.CategoryView(String.valueOf(category.id()), category.name())).toList(),
                item.tags().stream().map(tag -> new VideoDtos.TagView(String.valueOf(tag.id()), tag.name())).toList(),
                time(item.scheduledAt()), time(item.publishedAt()), item.watchTimeMs(), time(item.createdAt()), time(item.updatedAt()));
    }

    private VideoDtos.MediaView media(VideoAdminService.MediaView item) {
        if (item == null) return null;
        return new VideoDtos.MediaView(String.valueOf(item.id()), item.resourceType(), item.contentType(), item.fileSize(),
                item.width(), item.height(), item.durationMs(), item.url());
    }

    private String time(LocalDateTime value) { return value == null ? null : value.toString(); }
    private String traceId(HttpServletRequest request) { return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE); }
}
