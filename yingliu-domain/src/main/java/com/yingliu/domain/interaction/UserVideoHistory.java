package com.yingliu.domain.interaction;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 用户观看历史（user_video_history），联合主键 user_id + video_id */
@TableName("user_video_history")
public class UserVideoHistory implements Serializable {
    @TableId
    private Long userId;
    private Long videoId;
    private LocalDateTime lastViewedAt;
    private Integer viewCount;
    private Integer lastProgressMs;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public LocalDateTime getLastViewedAt() { return lastViewedAt; }
    public void setLastViewedAt(LocalDateTime lastViewedAt) { this.lastViewedAt = lastViewedAt; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
    public Integer getLastProgressMs() { return lastProgressMs; }
    public void setLastProgressMs(Integer lastProgressMs) { this.lastProgressMs = lastProgressMs; }
}
