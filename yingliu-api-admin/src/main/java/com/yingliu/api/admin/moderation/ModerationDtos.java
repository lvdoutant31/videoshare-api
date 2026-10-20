package com.yingliu.api.admin.moderation;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
public final class ModerationDtos {
 public record Comment(String id,String videoId,String videoTitle,String userId,String author,String content,String status,String riskLevel,long likeCount,long replyCount,String createdAt,String updatedAt,String handledReason,String parentCommentId,List<Reply> replies,List<History> history){}
 public record Reply(String id,String author,String content,String createdAt){}
 public record History(String action,String operator,String createdAt,String reason){}
 public record Report(String id,String reporterId,String targetType,String targetId,String reasonCode,String description,String status,String riskLevel,String assigneeAdminId,String resolution,String resolutionNote,String resolvedAt,String createdAt,String updatedAt,String targetSummary,String reporter,String reason,String assignee,String targetSnapshot,List<History> history){}
 public record Reason(@NotBlank String reason){}
 public record Resolve(@NotBlank @jakarta.validation.constraints.Pattern(regexp="RESOLVED") String resolution,@NotBlank @jakarta.validation.constraints.Size(max=300) String note){}
 public record Reject(@NotBlank @jakarta.validation.constraints.Size(max=300) String note){}
 public record Batch(List<Long> commentIds,@NotBlank String action,String reason){}
 public record BatchResult(List<Long> succeededIds,List<Long> failed){}
 private ModerationDtos(){}
}
