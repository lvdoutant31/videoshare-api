package com.yingliu.service.moderation;

import com.yingliu.service.content.shared.ServicePage;
import java.time.LocalDateTime;
import java.util.List;

public interface ModerationService {
    ServicePage<Comment> comments(long page, long size, String status, Long videoId, String keyword);
    Comment comment(long id);
    Comment hideComment(long id, long actor, String reason);
    Comment restoreComment(long id, long actor);
    Comment deleteComment(long id, long actor, String reason);
    void deleteComments(List<Long> ids, long actor, String reason);
    ServicePage<Report> reports(long page, long size, String status, String targetType);
    Report report(long id);
    void claimReport(long id, long actor);
    void resolveReport(long id, long actor, String resolution, String note);
    void rejectReport(long id, long actor, String note);

    record Comment(long id, long videoId, String videoTitle, long userId, String author, String content, String status, String riskLevel,
                   long likeCount, long replyCount, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    record Report(long id, long reporterId, String targetType, long targetId, String reasonCode, String description,
                  String status, String riskLevel, Long assigneeAdminId, String resolution, String resolutionNote,
                  LocalDateTime resolvedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
