package com.yingliu.domain.interaction;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 用户关注关系（user_follow），联合主键 follower_id + followee_id，禁止自关注 */
@TableName("user_follow")
public class UserFollow implements Serializable {
    @TableId
    private Long followerId;
    private Long followeeId;
    private LocalDateTime createdAt;

    public Long getFollowerId() { return followerId; }
    public void setFollowerId(Long followerId) { this.followerId = followerId; }
    public Long getFolloweeId() { return followeeId; }
    public void setFolloweeId(Long followeeId) { this.followeeId = followeeId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
