package com.yingliu.api.admin.moderation;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
public final class ModerationDtos {
 public record Comment(String id,String videoId,String videoTitle,String userId,String author,String content,String status,String riskLevel,long likeCount,long replyCount,String createdAt,String updatedAt){}
 public record Report(String id,String reporterId,String targetType,String targetId,String reasonCode,String description,String status,String riskLevel,String assigneeAdminId,String resolution,String resolutionNote,String resolvedAt,String createdAt,String updatedAt){}
 public record Reason(@NotBlank String reason){}
 public record Resolve(@NotBlank String resolution,String note){}
 public record Batch(List<Long> commentIds,@NotBlank String action,String reason){}
 public record BatchResult(List<Long> succeededIds,List<Long> failed){}
 private ModerationDtos(){}
}
