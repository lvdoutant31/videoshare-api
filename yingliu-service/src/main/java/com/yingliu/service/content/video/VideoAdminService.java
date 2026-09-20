package com.yingliu.service.content.video;

import com.yingliu.service.content.shared.ServicePage;

import java.time.LocalDateTime;
import java.util.List;

/** 视频管理用例契约，统一草稿、审核、发布和定时发布流程；身份与版本由调用方传入。 */
public interface VideoAdminService {
    ServicePage<VideoSummary> list(VideoQuery query);
    VideoDetail get(long videoId);
    VideoDetail update(long videoId, long actorAdminId, UpdateCommand command);
    VideoDetail createDraft(CreateDraftCommand command);
    long currentAuthorId(long actorAdminId);
    /** 领取当前版本的审核任务；currentVersion 用于避免对过时版本进行处理。 */
    VideoDetail claimReview(long videoId, long actorAdminId, int currentVersion);
    /** 释放审核任务，使其可以重新领取。 */
    VideoDetail releaseReview(long videoId, long actorAdminId, int currentVersion);
    VideoDetail requestRevision(long videoId, long actorAdminId, int currentVersion, String reason);
    VideoDetail submitForReview(long videoId, long actorAdminId);
    ServicePage<ReviewQueueItem> listReviewQueue(ReviewQueueQuery query);
    /** 通过指定版本审核，记录审核人和意见。 */
    VideoDetail approveReview(long videoId, long actorAdminId, int currentVersion, String reason);
    /** 驳回指定版本审核，保留驳回原因。 */
    VideoDetail rejectReview(long videoId, long actorAdminId, int currentVersion, String reason);
    VideoDetail publishNow(long videoId, long actorAdminId);
    /** 设置视频预定发布时间，保留业务现有时区与可发布状态校验。 */
    VideoDetail schedulePublish(long videoId, long actorAdminId, LocalDateTime scheduledAt);
    VideoDetail cancelScheduledPublish(long videoId, long actorAdminId);
    VideoDetail offShelf(long videoId, long actorAdminId, String reason);
    VideoDetail restore(long videoId, long actorAdminId);
    BatchOperationResult batchOffShelf(List<Long> videoIds, long actorAdminId, String reason);
    BatchOperationResult batchRestore(List<Long> videoIds, long actorAdminId);
    /** 批量删除（软删除），逐条处理并汇总失败项，删除原因必填。 */
    BatchOperationResult batchDelete(List<Long> videoIds, long actorAdminId, String reason);
    void retryTranscode(long videoId, String profile, long actorAdminId);
    void delete(long videoId, long actorAdminId, String reason);
    /** 领取到期或超过处理期限的发布计划；limit 限制本轮批量大小。 */
    List<Long> claimDuePublishSchedules(LocalDateTime expiredBefore, int limit);
    /** 发布已经领取的计划，供调度任务调用。 */
    void publishClaimedSchedule(long scheduleId);
    List<AuditHistoryItem> auditHistory(long videoId);

    record UpdateCommand(String title, String description, String visibility, List<Long> categoryIds,
                         List<Long> tagIds, Long originalMediaId) {
        public UpdateCommand(String title, String description, String visibility, List<Long> categoryIds, List<Long> tagIds) {
            this(title, description, visibility, categoryIds, tagIds, null);
        }
    }

    record CreateDraftCommand(long actorAdminId, long authorId, long originalMediaId, Long coverMediaId,
                              String title, String description, String sourceNote, List<Long> categoryIds,
                              List<Long> tagIds, String visibility) { }

    record VideoQuery(long page, long pageSize, String keyword, Long authorId, String auditStatus, String publishStatus) { }
    record ReviewQueueQuery(long page, long pageSize, String keyword, Long authorId, String riskLevel,
                            long actorAdminId) { }
    record VideoSummary(long id, long authorId, String authorNickname, String title, String auditStatus,
                        String publishStatus, String visibility, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    record ReviewQueueItem(long id, long authorId, String authorNickname, String title, String riskLevel,
                           int currentVersion, LocalDateTime submittedAt) { }
    record VideoDetail(long id, long authorId, String authorNickname, String title, String description,
                       String sourceNote, MediaView originalMedia, MediaView coverMedia, String auditStatus,
                       String publishStatus, String visibility, String riskLevel, int currentVersion,
                       List<CategoryView> categories, List<TagView> tags, LocalDateTime scheduledAt,
                       LocalDateTime publishedAt, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    record AuditHistoryItem(long id, int videoVersion, Long reviewerAdminId, String action, String reason,
                            LocalDateTime claimedAt, LocalDateTime reviewedAt, LocalDateTime createdAt) { }
    record MediaView(long id, String resourceType, String contentType, long fileSize, Integer width,
                     Integer height, Integer durationMs, String url) { }
    record CategoryView(long id, String name) { }
    record TagView(long id, String name) { }
    record BatchOperationResult(List<Long> succeeded, List<BatchFailure> failures) { }
    record BatchFailure(long videoId, String code, String message) { }
}
