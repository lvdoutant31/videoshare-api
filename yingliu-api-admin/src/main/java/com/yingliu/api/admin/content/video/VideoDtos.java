package com.yingliu.api.admin.content.video;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class VideoDtos {
    private VideoDtos() { }

    public record CreateDraft(String authorId, @NotBlank String originalMediaId, String coverMediaId,
                              @NotBlank @Size(max = 120) String title, @Size(max = 2000) String description,
                              @Size(max = 128) String sourceNote, List<@NotBlank String> categoryIds,
                              List<@NotBlank String> tagIds, String visibility) { }
    public record Update(@NotBlank @Size(max = 120) String title, @Size(max = 2000) String description,
                         @NotBlank String visibility, List<@NotBlank String> categoryIds, List<@NotBlank String> tagIds) { }
    public record Claim(@NotNull @Min(1) Integer version) { }
    public record ApproveReview(@NotNull @Min(1) Integer currentVersion, @Size(max = 1000) String reason) { }
    public record RejectReview(@NotNull @Min(1) Integer currentVersion, @NotBlank @Size(max = 1000) String reason) { }
    public record RequestRevision(@NotNull @Min(1) Integer currentVersion, @NotBlank @Size(max = 1000) String reason) { }

    public record SchedulePublish(@NotNull @jakarta.validation.constraints.Future LocalDateTime scheduledAt) { }
    public record Reason(@NotBlank @Size(max = 512) String reason) { }
    public record BatchVideoIds(@NotNull @Size(min = 1, max = 100) List<@NotBlank String> videoIds,
                                @Size(max = 512) String reason) { }

    public record MediaView(String id, String resourceType, String contentType, long fileSize, Integer width,
                            Integer height, Integer durationMs, String url) { }
    public record CategoryView(String id, String name) { }
    public record TagView(String id, String name) { }
    public record Summary(String id, String authorId, String authorNickname, String title, String auditStatus,
                          String publishStatus, String visibility, String createdAt, String updatedAt) { }
    public record ReviewQueueItem(String id, String authorId, String authorNickname, String title, String riskLevel,
                                  int currentVersion, String submittedAt) { }
    public record AuditHistoryItem(String id, int videoVersion, String reviewerAdminId, String action, String reason,
                                   String claimedAt, String reviewedAt, String createdAt) { }
    public record Detail(String id, String authorId, String authorNickname, String title, String description,
                         String sourceNote, MediaView originalMedia, MediaView coverMedia, String auditStatus,
                         String publishStatus, String visibility, String riskLevel, int currentVersion,
                         List<CategoryView> categories, List<TagView> tags, String scheduledAt, String publishedAt,
                         String createdAt, String updatedAt) { }
}
