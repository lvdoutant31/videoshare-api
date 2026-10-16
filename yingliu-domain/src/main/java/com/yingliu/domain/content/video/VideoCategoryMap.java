package com.yingliu.domain.content.video;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 视频与分类的关联记录；关系维护由视频业务事务统一处理。 */
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
