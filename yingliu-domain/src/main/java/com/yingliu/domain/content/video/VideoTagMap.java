package com.yingliu.domain.content.video;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("video_tag_map")
public class VideoTagMap {
    @TableId
    private Long videoId;
    private Long tagId;

    public VideoTagMap() { }
    public VideoTagMap(Long videoId, Long tagId) { this.videoId = videoId; this.tagId = tagId; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }
}
