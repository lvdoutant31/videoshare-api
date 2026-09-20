package com.yingliu.service.content.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.content.video.VideoTranscode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface VideoTranscodeMapper extends BaseMapper<VideoTranscode> {
    @Update("UPDATE video_transcode SET status='PROCESSING', started_at=#{leaseStartedAt}, finished_at=NULL, "
            + "error_code=NULL, error_message=NULL, updated_at=NOW(3) WHERE id=#{id} AND status='PENDING'")
    int claim(@Param("id") long id, @Param("leaseStartedAt") LocalDateTime leaseStartedAt);

    @Update("UPDATE video_transcode SET media_id=#{mediaId}, status='SUCCEEDED', finished_at=NOW(3), "
            + "error_code=NULL, error_message=NULL, updated_at=NOW(3) "
            + "WHERE id=#{id} AND status='PROCESSING' AND started_at=#{leaseStartedAt}")
    int complete(@Param("id") long id, @Param("leaseStartedAt") LocalDateTime leaseStartedAt,
                 @Param("mediaId") long mediaId);

    @Update("UPDATE video_transcode SET status='FAILED', finished_at=NOW(3), error_code=#{code}, "
            + "error_message=#{message}, updated_at=NOW(3) "
            + "WHERE id=#{id} AND status='PROCESSING' AND started_at=#{leaseStartedAt}")
    int fail(@Param("id") long id, @Param("leaseStartedAt") LocalDateTime leaseStartedAt,
             @Param("code") String code, @Param("message") String message);

    @Update("UPDATE video_transcode SET status='PENDING', started_at=NULL, finished_at=NULL, "
            + "error_code='LEASE_EXPIRED', error_message='转码进程中断，任务已自动恢复', "
            + "retry_count=retry_count+1, updated_at=NOW(3) "
            + "WHERE definition='720P' AND status='PROCESSING' AND started_at < #{expiredBefore}")
    int requeueStale(@Param("expiredBefore") LocalDateTime expiredBefore);
}
