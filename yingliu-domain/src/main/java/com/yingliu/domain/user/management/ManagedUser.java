package com.yingliu.domain.user.management;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 管理端用户资料映射，供用户查询和账号治理使用。 */
@TableName("sys_user")
public class ManagedUser {
    @TableId
    private Long id;
    private String nickname;
    private Long avatarMediaId;
    private String status;
    private LocalDateTime muteUntil;
    private LocalDateTime suspendUntil;
    private Integer violationCount;
    private Long followingCount;
    private Long followerCount;
    private LocalDateTime lastLoginAt;
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Long getAvatarMediaId() { return avatarMediaId; }
    public void setAvatarMediaId(Long avatarMediaId) { this.avatarMediaId = avatarMediaId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getMuteUntil() { return muteUntil; }
    public void setMuteUntil(LocalDateTime muteUntil) { this.muteUntil = muteUntil; }
    public LocalDateTime getSuspendUntil() { return suspendUntil; }
    public void setSuspendUntil(LocalDateTime suspendUntil) { this.suspendUntil = suspendUntil; }
    public Integer getViolationCount() { return violationCount; }
    public void setViolationCount(Integer violationCount) { this.violationCount = violationCount; }
    public Long getFollowingCount() { return followingCount; }
    public void setFollowingCount(Long followingCount) { this.followingCount = followingCount; }
    public Long getFollowerCount() { return followerCount; }
    public void setFollowerCount(Long followerCount) { this.followerCount = followerCount; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
