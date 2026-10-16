package com.yingliu.service.content.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.content.video.VideoPublishSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface VideoPublishScheduleMapper extends BaseMapper<VideoPublishSchedule> {
    @Update("UPDATE video_publish_schedule SET status = 'CANCELLED', updated_at = NOW(3) "
            + "WHERE video_id = #{videoId} AND status IN ('PENDING', 'PROCESSING')")
    int cancelActiveByVideoId(@Param("videoId") long videoId);

    @Select("SELECT id FROM video_publish_schedule WHERE "
            + "(status = 'PENDING' AND scheduled_at <= NOW(3)) "
            + "OR (status = 'PROCESSING' AND processing_started_at < #{expiredBefore}) "
            + "ORDER BY scheduled_at ASC, id ASC LIMIT #{limit}")
    List<Long> selectDueOrExpiredIds(@Param("expiredBefore") LocalDateTime expiredBefore, @Param("limit") int limit);

    @Update("UPDATE video_publish_schedule SET status = 'PROCESSING', processing_started_at = NOW(3), updated_at = NOW(3) "
            + "WHERE id = #{scheduleId} AND ((status = 'PENDING' AND scheduled_at <= NOW(3)) "
            + "OR (status = 'PROCESSING' AND processing_started_at < #{expiredBefore}))")
    int claimForProcessing(@Param("scheduleId") long scheduleId, @Param("expiredBefore") LocalDateTime expiredBefore);

    @Update("UPDATE video_publish_schedule SET status = 'EXECUTED', executed_at = NOW(3), updated_at = NOW(3) "
            + "WHERE id = #{scheduleId} AND status = 'PROCESSING'")
    int markExecuted(@Param("scheduleId") long scheduleId);

    @Update("UPDATE video_publish_schedule SET status = 'CANCELLED', updated_at = NOW(3) "
            + "WHERE id = #{scheduleId} AND status = 'PROCESSING'")
    int markCancelled(@Param("scheduleId") long scheduleId);
}
