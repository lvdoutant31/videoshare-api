package com.yingliu.domain.content.video;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 视频审核历史，保留被审核版本、处理人、处理意见及时间。 */
@TableName("video_audit_record")
public class VideoAuditRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long videoId;
    private Integer videoVersion;
    private Long reviewerAdminId;
    private String action;
    private String reason;
    private String machineRiskSnapshot;
    private LocalDateTime claimedAt;
    private LocalDateTime reviewedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public Integer getVideoVersion() { return videoVersion; }
    public void setVideoVersion(Integer videoVersion) { this.videoVersion = videoVersion; }
    public Long getReviewerAdminId() { return reviewerAdminId; }
    public void setReviewerAdminId(Long reviewerAdminId) { this.reviewerAdminId = reviewerAdminId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getMachineRiskSnapshot() { return machineRiskSnapshot; }
    public void setMachineRiskSnapshot(String machineRiskSnapshot) { this.machineRiskSnapshot = machineRiskSnapshot; }
    public LocalDateTime getClaimedAt() { return claimedAt; }
    public void setClaimedAt(LocalDateTime claimedAt) { this.claimedAt = claimedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
