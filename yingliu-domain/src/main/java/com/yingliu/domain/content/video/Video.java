package com.yingliu.domain.content.video;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 视频主记录，分别保存审核和发布状态；状态转换由视频业务用例执行。 */
@TableName("video")
public class Video {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long authorId;
    private String title;
    private String description;
    private String sourceNote;
    private Long coverMediaId;
    private Long originalMediaId;
    private String auditStatus;
    private String publishStatus;
    private String visibility;
    private String riskLevel;
    private Integer manualWeight;
    private LocalDateTime scheduledAt;
    private LocalDateTime publishedAt;
    private LocalDateTime offShelfAt;
    private String offShelfReason;
    private Integer currentVersion;
    @TableLogic
    private Integer deleted;
    private LocalDateTime deletedAt;
    private Long deletedBy;
    private Long createdBy;
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSourceNote() { return sourceNote; }
    public void setSourceNote(String sourceNote) { this.sourceNote = sourceNote; }
    public Long getCoverMediaId() { return coverMediaId; }
    public void setCoverMediaId(Long coverMediaId) { this.coverMediaId = coverMediaId; }
    public Long getOriginalMediaId() { return originalMediaId; }
    public void setOriginalMediaId(Long originalMediaId) { this.originalMediaId = originalMediaId; }
    public String getAuditStatus() { return auditStatus; }
    public void setAuditStatus(String auditStatus) { this.auditStatus = auditStatus; }
    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public Integer getManualWeight() { return manualWeight; }
    public void setManualWeight(Integer manualWeight) { this.manualWeight = manualWeight; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getOffShelfAt() { return offShelfAt; }
    public void setOffShelfAt(LocalDateTime offShelfAt) { this.offShelfAt = offShelfAt; }
    public String getOffShelfReason() { return offShelfReason; }
    public void setOffShelfReason(String offShelfReason) { this.offShelfReason = offShelfReason; }
    public Integer getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(Integer currentVersion) { this.currentVersion = currentVersion; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
