package com.yingliu.service.content.video;

import com.yingliu.service.content.shared.ServicePage;

import java.time.LocalDateTime;
import java.util.List;

public interface VideoAdminService {
    ServicePage<VideoSummary> list(VideoQuery query);
    VideoDetail get(long videoId);
    VideoDetail update(long videoId, long actorAdminId, UpdateCommand command);
    VideoDetail createDraft(CreateDraftCommand command);
    VideoDetail claimReview(long videoId, long actorAdminId, int currentVersion);
    VideoDetail requestRevision(long videoId, long actorAdminId, int currentVersion, String reason);
    VideoDetail submitForReview(long videoId, long actorAdminId);
    ServicePage<ReviewQueueItem> listReviewQueue(ReviewQueueQuery query);
    VideoDetail approveReview(long videoId, long actorAdminId, int currentVersion, String reason);
    VideoDetail rejectReview(long videoId, long actorAdminId, int currentVersion, String reason);
    VideoDetail publishNow(long videoId, long actorAdminId);
    VideoDetail schedulePublish(long videoId, long actorAdminId, LocalDateTime scheduledAt);
    VideoDetail cancelScheduledPublish(long videoId, long actorAdminId);
    VideoDetail offShelf(long videoId, long actorAdminId, String reason);
    VideoDetail restore(long videoId, long actorAdminId);
    BatchOperationResult batchOffShelf(List<Long> videoIds, long actorAdminId, String reason);
    BatchOperationResult batchRestore(List<Long> videoIds, long actorAdminId);
    void retryTranscode(long videoId, String profile, long actorAdminId);
    void delete(long videoId, long actorAdminId, String reason);
    List<Long> claimDuePublishSchedules(LocalDateTime expiredBefore, int limit);
    void publishClaimedSchedule(long scheduleId);
    List<AuditHistoryItem> auditHistory(long videoId);

    record UpdateCommand(String title, String description, String visibility, List<Long> categoryIds, List<Long> tagIds) { }

    record CreateDraftCommand(long actorAdminId, long authorId, long originalMediaId, Long coverMediaId,
                              String title, String description, String sourceNote, List<Long> categoryIds,
                              List<Long> tagIds, String visibility) { }

    record VideoQuery(long page, long pageSize, String keyword, Long authorId, String auditStatus, String publishStatus) { }
    record ReviewQueueQuery(long page, long pageSize, String keyword, Long authorId, String riskLevel) { }
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
                     Integer height, Integer durationMs) { }
    record CategoryView(long id, String name) { }
    record TagView(long id, String name) { }
    record BatchOperationResult(List<Long> succeeded, List<BatchFailure> failures) { }
    record BatchFailure(long videoId, String code, String message) { }
}
