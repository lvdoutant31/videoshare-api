package com.yingliu.service.content.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yingliu.domain.content.video.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VideoMapper extends BaseMapper<Video> {
    @Update("UPDATE video SET title = #{title}, description = #{description}, visibility = #{visibility}, current_version = current_version + 1, updated_by = #{actorAdminId}, updated_at = NOW(3) WHERE id = #{videoId} AND deleted = 0 AND current_version = #{currentVersion} AND publish_status <> 'DELETED'")
    int updateMetadata(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId,
                       @Param("currentVersion") int currentVersion, @Param("title") String title,
                       @Param("description") String description, @Param("visibility") String visibility);

    @Update("UPDATE video SET audit_status = 'CLAIMED', updated_by = #{actorAdminId}, updated_at = NOW(3) WHERE id = #{videoId} AND deleted = 0 AND audit_status = 'PENDING_REVIEW' AND current_version = #{currentVersion}")
    int claimReview(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId, @Param("currentVersion") int currentVersion);

    @Update("UPDATE video SET audit_status = 'REVISION_REQUIRED', updated_by = #{actorAdminId}, updated_at = NOW(3) WHERE id = #{videoId} AND deleted = 0 AND audit_status IN ('PENDING_REVIEW','CLAIMED') AND current_version = #{currentVersion}")
    int requestRevision(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId, @Param("currentVersion") int currentVersion);
    @Update("UPDATE video SET audit_status = 'PENDING_REVIEW', updated_by = #{actorAdminId}, updated_at = NOW(3) "
            + "WHERE id = #{videoId} AND deleted = 0 AND audit_status = 'DRAFT' AND publish_status = 'UNPUBLISHED'")
    int submitForReview(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId);

    @Update("UPDATE video SET audit_status = 'APPROVED', updated_by = #{actorAdminId}, updated_at = NOW(3) "
            + "WHERE id = #{videoId} AND deleted = 0 AND audit_status = 'PENDING_REVIEW' "
            + "AND publish_status = 'UNPUBLISHED' AND current_version = #{currentVersion}")
    int approveReview(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId,
                      @Param("currentVersion") int currentVersion);

    @Update("UPDATE video SET audit_status = 'REJECTED', updated_by = #{actorAdminId}, updated_at = NOW(3) "
            + "WHERE id = #{videoId} AND deleted = 0 AND audit_status = 'PENDING_REVIEW' "
            + "AND publish_status = 'UNPUBLISHED' AND current_version = #{currentVersion}")
    int rejectReview(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId,
                     @Param("currentVersion") int currentVersion);

    @Update("UPDATE video SET publish_status = 'PUBLISHED', scheduled_at = NULL, published_at = NOW(3), "
            + "updated_by = #{actorAdminId}, updated_at = NOW(3) WHERE id = #{videoId} AND deleted = 0 "
            + "AND audit_status = 'APPROVED' AND publish_status = 'UNPUBLISHED'")
    int publishNow(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId);

    @Update("UPDATE video SET publish_status = 'SCHEDULED', scheduled_at = #{scheduledAt}, updated_by = #{actorAdminId}, "
            + "updated_at = NOW(3) WHERE id = #{videoId} AND deleted = 0 AND audit_status = 'APPROVED' "
            + "AND publish_status = 'UNPUBLISHED'")
    int schedulePublish(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId,
                        @Param("scheduledAt") java.time.LocalDateTime scheduledAt);

    @Update("UPDATE video SET publish_status = 'UNPUBLISHED', scheduled_at = NULL, updated_by = #{actorAdminId}, "
            + "updated_at = NOW(3) WHERE id = #{videoId} AND deleted = 0 AND audit_status = 'APPROVED' "
            + "AND publish_status = 'SCHEDULED'")
    int cancelScheduledPublish(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId);

    @Update("UPDATE video SET publish_status = 'PUBLISHED', scheduled_at = NULL, published_at = NOW(3), updated_at = NOW(3) "
            + "WHERE id = #{videoId} AND deleted = 0 AND audit_status = 'APPROVED' AND publish_status = 'SCHEDULED'")
    int publishScheduled(@Param("videoId") long videoId);

    @Update("UPDATE video SET publish_status = 'OFF_SHELF', off_shelf_at = NOW(3), off_shelf_reason = #{reason}, "
            + "updated_by = #{actorAdminId}, updated_at = NOW(3) WHERE id = #{videoId} AND deleted = 0 "
            + "AND audit_status = 'APPROVED' AND publish_status = 'PUBLISHED'")
    int offShelf(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId, @Param("reason") String reason);

    @Update("UPDATE video SET publish_status = 'PUBLISHED', off_shelf_at = NULL, off_shelf_reason = NULL, "
            + "updated_by = #{actorAdminId}, updated_at = NOW(3) WHERE id = #{videoId} AND deleted = 0 "
            + "AND audit_status = 'APPROVED' AND publish_status = 'OFF_SHELF'")
    int restore(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId);

    @Update("UPDATE video_transcode t JOIN video v ON v.id=t.video_id SET t.status='PENDING', t.error_code=NULL, t.error_message=NULL, t.started_at=NULL, t.finished_at=NULL, t.retry_count=t.retry_count+1, t.updated_at=NOW(3) WHERE t.video_id=#{videoId} AND t.definition=#{profile} AND t.status IN ('FAILED','CANCELLED')")
    int retryTranscode(@Param("videoId") long videoId, @Param("profile") String profile);

    @Update("UPDATE sys_async_task SET status='PENDING', next_retry_at=NULL, last_error_code=NULL, last_error_message=NULL, updated_at=NOW(3) WHERE task_type='VIDEO_TRANSCODE' AND business_type='VIDEO' AND business_id=#{videoId} AND status IN ('FAILED','CANCELLED')")
    int retryTranscodeTask(@Param("videoId") long videoId);

    @Update("UPDATE video SET deleted = 1, deleted_at = NOW(3), deleted_by = #{actorAdminId}, publish_status = 'DELETED', "
            + "updated_by = #{actorAdminId}, updated_at = NOW(3) WHERE id = #{videoId} AND deleted = 0")
    int deleteVideo(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId);

    @Select("<script>SELECT v.* FROM video v WHERE v.deleted = 0 AND v.audit_status = 'APPROVED' "
            + "AND v.publish_status = 'PUBLISHED' AND v.visibility = 'PUBLIC' AND v.published_at IS NOT NULL "
            + "AND v.off_shelf_at IS NULL AND EXISTS (SELECT 1 FROM sys_user u WHERE u.id = v.author_id "
            + "AND u.status = 'NORMAL' AND u.deleted = 0) AND EXISTS (SELECT 1 FROM media_resource m "
            + "WHERE m.id = v.original_media_id AND m.resource_type = 'VIDEO_ORIGINAL' AND m.storage_state = 'READY') "
            + "<if test='publishedAt != null'>AND (v.published_at &lt; #{publishedAt} OR (v.published_at = #{publishedAt} "
            + "AND v.id &lt; #{videoId})) </if>ORDER BY v.published_at DESC, v.id DESC LIMIT #{limit}</script>")
    java.util.List<Video> selectPublicFeed(@Param("publishedAt") java.time.LocalDateTime publishedAt,
                                           @Param("videoId") Long videoId, @Param("limit") int limit);

    @Select("SELECT v.* FROM video v WHERE v.id = #{videoId} AND v.deleted = 0 AND v.audit_status = 'APPROVED' "
            + "AND v.publish_status = 'PUBLISHED' AND v.visibility = 'PUBLIC' AND v.published_at IS NOT NULL "
            + "AND v.off_shelf_at IS NULL AND EXISTS (SELECT 1 FROM sys_user u WHERE u.id = v.author_id "
            + "AND u.status = 'NORMAL' AND u.deleted = 0) AND EXISTS (SELECT 1 FROM media_resource m "
            + "WHERE m.id = v.original_media_id AND m.resource_type = 'VIDEO_ORIGINAL' AND m.storage_state = 'READY')")
    Video selectPublicById(@Param("videoId") long videoId);

    @Select("<script>SELECT * FROM video WHERE deleted = 0 AND audit_status = 'PENDING_REVIEW' "
            + "<if test='keyword != null and keyword != \"\"'>AND title LIKE CONCAT('%', #{keyword}, '%') </if>"
            + "<if test='authorId != null'>AND author_id = #{authorId} </if>"
            + "<if test='riskLevel != null and riskLevel != \"\"'>AND risk_level = #{riskLevel} </if>"
            + "ORDER BY FIELD(risk_level, 'HIGH', 'MEDIUM', 'LOW', 'NONE'), updated_at ASC, id ASC</script>")
    Page<Video> selectReviewPage(Page<Video> page, @Param("keyword") String keyword,
                                  @Param("authorId") Long authorId, @Param("riskLevel") String riskLevel);
}
