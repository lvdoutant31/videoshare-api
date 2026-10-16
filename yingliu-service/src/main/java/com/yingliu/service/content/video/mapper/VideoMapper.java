package com.yingliu.service.content.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yingliu.domain.content.video.Video;
import com.yingliu.service.content.video.DiscoverQueryRows;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VideoMapper extends BaseMapper<Video> {
    @Update("UPDATE video SET cover_media_id = #{coverMediaId}, updated_at = NOW(3) WHERE id = #{videoId} AND cover_media_id IS NULL")
    int attachGeneratedCover(@Param("videoId") long videoId, @Param("coverMediaId") long coverMediaId);

    String PUBLIC_VIDEO_WHERE = "v.deleted = 0 AND v.audit_status = 'APPROVED' "
            + "AND v.publish_status = 'PUBLISHED' AND v.visibility = 'PUBLIC' AND v.published_at IS NOT NULL "
            + "AND v.off_shelf_at IS NULL AND EXISTS (SELECT 1 FROM sys_user u WHERE u.id = v.author_id "
            + "AND u.status = 'NORMAL' AND u.deleted = 0) AND (v.original_media_id IS NULL OR EXISTS (SELECT 1 FROM media_resource ready_media "
            + "WHERE ready_media.id = v.original_media_id AND ready_media.resource_type = 'VIDEO_ORIGINAL' "
            + "AND ready_media.storage_state = 'READY')) ";

    @Select("SELECT COUNT(*) AS total_videos, "
            + "COALESCE((SELECT SUM(d.watch_time_ms) FROM video_watch_daily d JOIN video watch_v ON watch_v.id = d.video_id "
            + "WHERE watch_v.deleted = 0 AND watch_v.audit_status = 'APPROVED' AND watch_v.publish_status = 'PUBLISHED' "
            + "AND watch_v.visibility = 'PUBLIC' AND watch_v.off_shelf_at IS NULL), 0) AS total_watch_time_ms, "
            + "COALESCE((SELECT SUM(d.effective_play_count) FROM video_watch_daily d JOIN video watch_v ON watch_v.id = d.video_id "
            + "WHERE watch_v.deleted = 0 AND watch_v.audit_status = 'APPROVED' AND watch_v.publish_status = 'PUBLISHED' "
            + "AND watch_v.visibility = 'PUBLIC' AND watch_v.off_shelf_at IS NULL), 0) AS effective_play_count, "
            + "COALESCE((SELECT SUM(d.watch_time_ms) / NULLIF(SUM(d.effective_play_count), 0) FROM video_watch_daily d "
            + "JOIN video watch_v ON watch_v.id = d.video_id WHERE watch_v.deleted = 0 AND watch_v.audit_status = 'APPROVED' "
            + "AND watch_v.publish_status = 'PUBLISHED' AND watch_v.visibility = 'PUBLIC' AND watch_v.off_shelf_at IS NULL), 0) "
            + "AS average_watch_time_ms, "
            + "SUM(CASE WHEN v.published_at >= DATE_SUB(NOW(3), INTERVAL 24 HOUR) THEN 1 ELSE 0 END) AS today_new_count "
            + "FROM video v WHERE " + PUBLIC_VIDEO_WHERE)
    DiscoverQueryRows.Summary selectDiscoverSummary();

    @Select("SELECT c.id, c.name, COUNT(DISTINCT v.id) AS video_count FROM content_category c "
            + "JOIN video_category_map vc ON vc.category_id = c.id JOIN video v ON v.id = vc.video_id "
            + "WHERE c.deleted = 0 AND c.status = 'ENABLED' AND " + PUBLIC_VIDEO_WHERE
            + "GROUP BY c.id, c.name, c.sort ORDER BY video_count DESC, c.sort ASC, c.id ASC")
    java.util.List<DiscoverQueryRows.CategoryStat> selectDiscoverCategoryStats();

    @Select("SELECT "
            + "SUM(CASE WHEN ws.watch_time_ms < 10000 THEN 1 ELSE 0 END) AS under_ten_seconds, "
            + "SUM(CASE WHEN ws.watch_time_ms >= 10000 AND ws.watch_time_ms < 30000 THEN 1 ELSE 0 END) AS ten_to_thirty_seconds, "
            + "SUM(CASE WHEN ws.watch_time_ms >= 30000 AND ws.watch_time_ms < 60000 THEN 1 ELSE 0 END) AS thirty_to_sixty_seconds, "
            + "SUM(CASE WHEN ws.watch_time_ms >= 60000 AND ws.watch_time_ms < 180000 THEN 1 ELSE 0 END) AS one_to_three_minutes, "
            + "SUM(CASE WHEN ws.watch_time_ms >= 180000 THEN 1 ELSE 0 END) AS over_three_minutes "
            + "FROM video_watch_session ws JOIN video v ON v.id = ws.video_id WHERE " + PUBLIC_VIDEO_WHERE)
    DiscoverQueryRows.DurationStats selectDiscoverDurationStats();

    @Select("<script>SELECT video_id, COALESCE(SUM(watch_time_ms), 0) AS watch_time_ms "
            + "FROM video_watch_daily WHERE video_id IN "
            + "<foreach collection='videoIds' item='videoId' open='(' separator=',' close=')'>#{videoId}</foreach> "
            + "GROUP BY video_id</script>")
    java.util.List<DiscoverQueryRows.WatchTime> selectWatchTimes(@Param("videoIds") java.util.Collection<Long> videoIds);

    @Select("SELECT DISTINCT t.id, t.name FROM content_tag t JOIN video_tag_map vt ON vt.tag_id = t.id "
            + "JOIN video v ON v.id = vt.video_id WHERE t.deleted = 0 AND t.status = 'ENABLED' "
            + "AND t.merged_to_id IS NULL AND " + PUBLIC_VIDEO_WHERE + "ORDER BY t.name ASC, t.id ASC")
    java.util.List<DiscoverQueryRows.TagOption> selectDiscoverTags();

    // 每日热度衰减 20%；对数压缩播放和互动次数，避免少数高流量内容长期占榜。
    String DISCOVER_HEAT = "(LN(1 + COALESCE(w.plays, 0)) + 3 * LN(1 + COALESCE(l.likes, 0)) "
            + "+ 4 * LN(1 + COALESCE(f.favorites, 0)) + 2 * LN(1 + COALESCE(c.comments, 0)))";
    String DISCOVER_FROM = " FROM video v "
            + "LEFT JOIN (SELECT video_id, SUM(effective_play_count * POW(0.8, DATEDIFF(UTC_DATE(), metric_date))) AS plays "
            + "FROM video_watch_daily WHERE metric_date BETWEEN DATE_SUB(UTC_DATE(), INTERVAL 6 DAY) AND UTC_DATE() GROUP BY video_id) w ON w.video_id = v.id "
            + "LEFT JOIN (SELECT video_id, SUM(POW(0.8, DATEDIFF(CURRENT_DATE, DATE(created_at)))) AS likes FROM user_video_like "
            + "WHERE created_at BETWEEN DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY) AND NOW(3) GROUP BY video_id) l ON l.video_id = v.id "
            + "LEFT JOIN (SELECT video_id, SUM(POW(0.8, DATEDIFF(CURRENT_DATE, DATE(created_at)))) AS favorites FROM user_video_favorite "
            + "WHERE created_at BETWEEN DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY) AND NOW(3) GROUP BY video_id) f ON f.video_id = v.id "
            + "LEFT JOIN (SELECT video_id, SUM(POW(0.8, DATEDIFF(CURRENT_DATE, DATE(created_at)))) AS comments FROM video_comment "
            + "WHERE status = 'NORMAL' AND deleted_at IS NULL AND created_at BETWEEN DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY) AND NOW(3) "
            + "GROUP BY video_id) c ON c.video_id = v.id WHERE " + PUBLIC_VIDEO_WHERE;
    String DISCOVER_FILTERS = "<if test='keyword != null'>AND (v.title LIKE CONCAT('%', #{keywordPattern}, '%') ESCAPE '=' "
            + "OR EXISTS (SELECT 1 FROM video_tag_map keyword_vt JOIN content_tag keyword_t ON keyword_t.id = keyword_vt.tag_id "
            + "WHERE keyword_vt.video_id = v.id AND keyword_t.deleted = 0 AND keyword_t.status = 'ENABLED' "
            + "AND keyword_t.name LIKE CONCAT('%', #{keywordPattern}, '%') ESCAPE '=')) </if>"
            + "<if test='categoryId != null'>AND EXISTS (SELECT 1 FROM video_category_map filter_vc "
            + "WHERE filter_vc.video_id = v.id AND filter_vc.category_id = #{categoryId}) </if>"
            + "<if test='tagId != null'>AND EXISTS (SELECT 1 FROM video_tag_map filter_vt "
            + "WHERE filter_vt.video_id = v.id AND filter_vt.tag_id = #{tagId}) </if>";

    @Select("<script>SELECT v.*, " + DISCOVER_HEAT + " AS heat_score, "
            + "CASE WHEN #{keyword} IS NULL THEN 0 WHEN v.title = #{keyword} THEN 4 "
            + "WHEN v.title LIKE CONCAT(#{keywordPattern}, '%') ESCAPE '=' THEN 3 "
            + "WHEN v.title LIKE CONCAT('%', #{keywordPattern}, '%') ESCAPE '=' THEN 2 ELSE 1 END AS relevance_score "
            + DISCOVER_FROM + DISCOVER_FILTERS + " ORDER BY "
            + "<choose><when test='sort == \"LATEST\"'>v.published_at DESC, </when>"
            + "<when test='sort == \"HOT\"'>heat_score DESC, v.published_at DESC, </when>"
            + "<otherwise>relevance_score DESC, heat_score DESC, v.published_at DESC, </otherwise></choose>"
            + "v.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    java.util.List<Video> selectDiscoverVideos(@Param("keyword") String keyword,
                                                @Param("keywordPattern") String keywordPattern,
                                                @Param("categoryId") Long categoryId,
                                                @Param("tagId") Long tagId,
                                                @Param("sort") String sort,
                                                @Param("limit") int limit, @Param("offset") long offset);

    @Select("<script>SELECT v.*, " + DISCOVER_HEAT + " AS heat_score " + DISCOVER_FROM
            + "AND v.published_at BETWEEN DATE_SUB(NOW(3), INTERVAL 3 DAY) AND NOW(3) "
            + "<if test='excludedIds != null and !excludedIds.isEmpty()'>AND v.id NOT IN "
            + "<foreach collection='excludedIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if> "
            + "ORDER BY heat_score DESC, v.published_at DESC, v.id DESC LIMIT 1</script>")
    Video selectFreshDiscoverVideo(@Param("excludedIds") java.util.List<Long> excludedIds);
    @Update("UPDATE video SET title = #{title}, description = #{description}, visibility = #{visibility}, original_media_id = #{originalMediaId}, current_version = current_version + 1, updated_by = #{actorAdminId}, updated_at = NOW(3) WHERE id = #{videoId} AND deleted = 0 AND current_version = #{currentVersion} AND publish_status <> 'DELETED'")
    int updateMetadata(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId,
                       @Param("currentVersion") int currentVersion, @Param("title") String title,
                       @Param("description") String description, @Param("visibility") String visibility,
                       @Param("originalMediaId") Long originalMediaId);

    @Update("UPDATE video SET audit_status = 'CLAIMED', review_claimed_by = #{actorAdminId}, review_claimed_at = NOW(3), updated_by = #{actorAdminId}, updated_at = NOW(3) WHERE id = #{videoId} AND deleted = 0 AND audit_status = 'PENDING_REVIEW' AND current_version = #{currentVersion}")
    int claimReview(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId, @Param("currentVersion") int currentVersion);

    @Update("UPDATE video SET audit_status = 'PENDING_REVIEW', review_claimed_by = NULL, review_claimed_at = NULL, updated_by = #{actorAdminId}, updated_at = NOW(3) WHERE id = #{videoId} AND deleted = 0 AND audit_status = 'CLAIMED' AND current_version = #{currentVersion}")
    int releaseReview(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId, @Param("currentVersion") int currentVersion);

    @Update("UPDATE video SET audit_status = 'REVISION_REQUIRED', review_claimed_by = NULL, review_claimed_at = NULL, updated_by = #{actorAdminId}, updated_at = NOW(3) WHERE id = #{videoId} AND deleted = 0 AND audit_status = 'CLAIMED' AND review_claimed_by = #{actorAdminId} AND current_version = #{currentVersion}")
    int requestRevision(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId, @Param("currentVersion") int currentVersion);
    @Update("UPDATE video SET audit_status = 'PENDING_REVIEW', review_claimed_by = NULL, review_claimed_at = NULL, updated_by = #{actorAdminId}, updated_at = NOW(3) "
            + "WHERE id = #{videoId} AND deleted = 0 AND audit_status IN ('DRAFT','REJECTED','REVISION_REQUIRED') AND publish_status = 'UNPUBLISHED'")
    int submitForReview(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId);

    @Update("UPDATE video SET audit_status = 'APPROVED', review_claimed_by = NULL, review_claimed_at = NULL, updated_by = #{actorAdminId}, updated_at = NOW(3) "
            + "WHERE id = #{videoId} AND deleted = 0 AND audit_status = 'CLAIMED' AND review_claimed_by = #{actorAdminId} "
            + "AND publish_status = 'UNPUBLISHED' AND current_version = #{currentVersion}")
    int approveReview(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId,
                      @Param("currentVersion") int currentVersion);

    @Update("UPDATE video SET audit_status = 'REJECTED', review_claimed_by = NULL, review_claimed_at = NULL, updated_by = #{actorAdminId}, updated_at = NOW(3) "
            + "WHERE id = #{videoId} AND deleted = 0 AND audit_status = 'CLAIMED' AND review_claimed_by = #{actorAdminId} "
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

    @Update("UPDATE video SET audit_status = 'PENDING_REVIEW', publish_status = 'UNPUBLISHED', "
            + "published_at = NULL, scheduled_at = NULL, off_shelf_at = NULL, off_shelf_reason = NULL, "
            + "review_claimed_by = NULL, review_claimed_at = NULL, updated_by = #{actorAdminId}, updated_at = NOW(3) "
            + "WHERE id = #{videoId} AND deleted = 0 AND audit_status = 'APPROVED' AND publish_status = 'OFF_SHELF'")
    int restore(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId);

    @Update("UPDATE video_transcode t JOIN video v ON v.id=t.video_id SET t.status='PENDING', t.error_code=NULL, t.error_message=NULL, t.started_at=NULL, t.finished_at=NULL, t.retry_count=t.retry_count+1, t.updated_at=NOW(3) WHERE t.video_id=#{videoId} AND t.definition=#{profile} AND t.status IN ('FAILED','CANCELLED')")
    int retryTranscode(@Param("videoId") long videoId, @Param("profile") String profile);

    @Update("UPDATE sys_async_task SET status='PENDING', next_retry_at=NULL, last_error_code=NULL, last_error_message=NULL, updated_at=NOW(3) WHERE task_type='VIDEO_TRANSCODE' AND business_type='VIDEO' AND business_id=#{videoId} AND status IN ('FAILED','CANCELLED')")
    int retryTranscodeTask(@Param("videoId") long videoId);

    @Update("UPDATE video SET deleted = 1, deleted_at = NOW(3), deleted_by = #{actorAdminId}, publish_status = 'DELETED', "
            + "updated_by = #{actorAdminId}, updated_at = NOW(3) WHERE id = #{videoId} AND deleted = 0")
    int deleteVideo(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId);

    @Update("UPDATE video_comment SET status = 'DELETED', deleted_at = NOW(3), deleted_by = #{actorAdminId}, "
            + "delete_reason = #{reason}, updated_at = NOW(3) WHERE video_id = #{videoId} AND status <> 'DELETED'")
    int deleteCommentsByVideoId(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId,
                                @Param("reason") String reason);

    @Select("<script>SELECT v.* FROM video v WHERE v.deleted = 0 AND v.audit_status = 'APPROVED' "
            + "AND v.publish_status = 'PUBLISHED' AND v.visibility = 'PUBLIC' AND v.published_at IS NOT NULL "
            + "AND v.off_shelf_at IS NULL AND EXISTS (SELECT 1 FROM sys_user u WHERE u.id = v.author_id "
            + "AND u.status = 'NORMAL' AND u.deleted = 0) AND (v.original_media_id IS NULL OR EXISTS (SELECT 1 FROM media_resource m "
            + "WHERE m.id = v.original_media_id AND m.resource_type = 'VIDEO_ORIGINAL' AND m.storage_state = 'READY')) "
            + "<if test='publishedAt != null'>AND (v.published_at &lt; #{publishedAt} OR (v.published_at = #{publishedAt} "
            + "AND v.id &lt; #{videoId})) </if>ORDER BY v.published_at DESC, v.id DESC LIMIT #{limit}</script>")
    java.util.List<Video> selectPublicFeed(@Param("publishedAt") java.time.LocalDateTime publishedAt,
                                           @Param("videoId") Long videoId, @Param("limit") int limit);

    @Select("<script>SELECT v.* FROM video v JOIN user_follow f ON f.followee_id = v.author_id "
            + "WHERE f.follower_id = #{viewerId} AND v.deleted = 0 AND v.audit_status = 'APPROVED' "
            + "AND v.publish_status = 'PUBLISHED' AND v.visibility = 'PUBLIC' AND v.published_at IS NOT NULL "
            + "AND v.off_shelf_at IS NULL AND EXISTS (SELECT 1 FROM sys_user u WHERE u.id = v.author_id "
            + "AND u.status = 'NORMAL' AND u.deleted = 0) AND (v.original_media_id IS NULL OR EXISTS (SELECT 1 FROM media_resource m "
            + "WHERE m.id = v.original_media_id AND m.resource_type = 'VIDEO_ORIGINAL' AND m.storage_state = 'READY')) "
            + "<if test='publishedAt != null'>AND (v.published_at &lt; #{publishedAt} OR (v.published_at = #{publishedAt} "
            + "AND v.id &lt; #{videoId})) </if>ORDER BY v.published_at DESC, v.id DESC LIMIT #{limit}</script>")
    java.util.List<Video> selectFollowingFeed(@Param("viewerId") long viewerId,
                                              @Param("publishedAt") java.time.LocalDateTime publishedAt,
                                              @Param("videoId") Long videoId, @Param("limit") int limit);

    @Select("SELECT v.* FROM video v WHERE v.id = #{videoId} AND v.deleted = 0 AND v.audit_status = 'APPROVED' "
            + "AND v.publish_status = 'PUBLISHED' AND v.visibility = 'PUBLIC' AND v.published_at IS NOT NULL "
            + "AND v.off_shelf_at IS NULL AND EXISTS (SELECT 1 FROM sys_user u WHERE u.id = v.author_id "
            + "AND u.status = 'NORMAL' AND u.deleted = 0) AND (v.original_media_id IS NULL OR EXISTS (SELECT 1 FROM media_resource m "
            + "WHERE m.id = v.original_media_id AND m.resource_type = 'VIDEO_ORIGINAL' AND m.storage_state = 'READY'))")
    Video selectPublicById(@Param("videoId") long videoId);

    /** 发布评论时锁定视频行，和管理端删除视频使用相同的锁顺序。 */
    @Select("SELECT v.* FROM video v WHERE v.id = #{videoId} AND v.deleted = 0 AND v.audit_status = 'APPROVED' "
            + "AND v.publish_status = 'PUBLISHED' AND v.visibility = 'PUBLIC' AND v.published_at IS NOT NULL "
            + "AND v.off_shelf_at IS NULL AND EXISTS (SELECT 1 FROM sys_user u WHERE u.id = v.author_id "
            + "AND u.status = 'NORMAL' AND u.deleted = 0) AND (v.original_media_id IS NULL OR EXISTS (SELECT 1 FROM media_resource m "
            + "WHERE m.id = v.original_media_id AND m.resource_type = 'VIDEO_ORIGINAL' AND m.storage_state = 'READY')) FOR UPDATE")
    Video selectPublicByIdForUpdate(@Param("videoId") long videoId);

    /** 作者公开视频 ID（游标按 id 倒序；作者主页网格） */
    @Select("<script>SELECT v.id FROM video v WHERE v.author_id = #{authorId} AND " + PUBLIC_VIDEO_WHERE
            + "<if test='cursorId != null'>AND v.id &lt; #{cursorId} </if>"
            + "ORDER BY v.id DESC LIMIT #{limit}</script>")
    java.util.List<Long> selectPublicIdsByAuthor(@Param("authorId") long authorId,
                                                 @Param("cursorId") Long cursorId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM video v WHERE v.author_id = #{authorId} AND " + PUBLIC_VIDEO_WHERE)
    long countPublicByAuthor(@Param("authorId") long authorId);

    @Select("<script>SELECT v.author_id AS authorId, COUNT(*) AS total FROM video v WHERE " + PUBLIC_VIDEO_WHERE
            + "AND v.author_id IN <foreach collection='authorIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY v.author_id</script>")
    java.util.List<AuthorContentCount> countPublicByAuthors(@Param("authorIds") java.util.List<Long> authorIds);

    record AuthorContentCount(long authorId, long total) { }

    @Select("<script>SELECT * FROM video WHERE deleted = 0 AND (audit_status = 'PENDING_REVIEW' "
            + "OR (audit_status = 'CLAIMED' AND review_claimed_by = #{actorAdminId})) "
            + "<if test='keyword != null and keyword != \"\"'>AND title LIKE CONCAT('%', #{keyword}, '%') </if>"
            + "<if test='authorId != null'>AND author_id = #{authorId} </if>"
            + "<if test='riskLevel != null and riskLevel != \"\"'>AND risk_level = #{riskLevel} </if>"
            + "ORDER BY FIELD(risk_level, 'HIGH', 'MEDIUM', 'LOW', 'NONE'), updated_at ASC, id ASC</script>")
    Page<Video> selectReviewPage(Page<Video> page, @Param("keyword") String keyword,
                                  @Param("authorId") Long authorId, @Param("riskLevel") String riskLevel,
                                  @Param("actorAdminId") long actorAdminId);
}
