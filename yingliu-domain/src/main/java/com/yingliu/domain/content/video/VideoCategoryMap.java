package com.yingliu.domain.content.video;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("video_category_map")
public class VideoCategoryMap {
    @TableId
    private Long videoId;
    private Long categoryId;

    public VideoCategoryMap() { }
    public VideoCategoryMap(Long videoId, Long categoryId) { this.videoId = videoId; this.categoryId = categoryId; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
}
