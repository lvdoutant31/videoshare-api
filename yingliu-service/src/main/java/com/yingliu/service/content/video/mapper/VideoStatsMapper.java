package com.yingliu.service.content.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.content.video.VideoStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VideoStatsMapper extends BaseMapper<VideoStats> {

    @Update("INSERT INTO video_stats (video_id, play_count, like_count, favorite_count, comment_count, updated_at) "
            + "VALUES (#{videoId}, 1, 0, 0, 0, NOW(3)) "
            + "ON DUPLICATE KEY UPDATE play_count = play_count + 1, updated_at = NOW(3)")
    int incrementPlay(@Param("videoId") long videoId);

    @Update("INSERT INTO video_watch_daily "
            + "(metric_date, video_id, effective_play_count, watch_time_ms, complete_count, calculated_at) "
            + "VALUES (#{metricDate}, #{videoId}, 1, 0, 0, NOW(3)) "
            + "ON DUPLICATE KEY UPDATE effective_play_count = effective_play_count + 1, calculated_at = NOW(3)")
    int incrementDailyArticlePlay(@Param("videoId") long videoId, @Param("metricDate") java.time.LocalDate metricDate);

    @Select("<script>SELECT video_id, COUNT(*) AS like_count FROM user_video_like WHERE video_id IN "
            + "<foreach collection='videoIds' item='videoId' open='(' separator=',' close=')'>#{videoId}</foreach> "
            + "GROUP BY video_id</script>")
    java.util.List<com.yingliu.service.content.video.DiscoverQueryRows.LikeCount> selectLikeCounts(
            @Param("videoIds") java.util.Collection<Long> videoIds);

    /** 互动计数原子增减（防并发丢更新）；不存在时插入初始行 */
    @Update("INSERT INTO video_stats (video_id, play_count, like_count, favorite_count, comment_count, updated_at) "
            + "VALUES (#{videoId}, 0, 0, 0, 0, NOW(3)) "
            + "ON DUPLICATE KEY UPDATE like_count = GREATEST(0, CAST(like_count AS SIGNED) + #{likeDelta}), updated_at = NOW(3)")
    int adjustLike(@Param("videoId") long videoId, @Param("likeDelta") int likeDelta);

    @Update("INSERT INTO video_stats (video_id, play_count, like_count, favorite_count, comment_count, updated_at) "
            + "VALUES (#{videoId}, 0, 0, 0, 0, NOW(3)) "
            + "ON DUPLICATE KEY UPDATE favorite_count = GREATEST(0, CAST(favorite_count AS SIGNED) + #{favoriteDelta}), updated_at = NOW(3)")
    int adjustFavorite(@Param("videoId") long videoId, @Param("favoriteDelta") int favoriteDelta);

    /** 当前读，避免默认 REPEATABLE READ 下并发幂等请求返回旧快照。 */
    @Select("SELECT like_count FROM video_stats WHERE video_id = #{videoId} FOR UPDATE")
    Long selectLikeCountForUpdate(@Param("videoId") long videoId);

    @Update("UPDATE video_stats SET like_count = #{likeCount}, updated_at = NOW(3) WHERE video_id = #{videoId} AND like_count = 0")
    int setLikeCount(@Param("videoId") long videoId, @Param("likeCount") long likeCount);

    @Select("SELECT favorite_count FROM video_stats WHERE video_id = #{videoId} FOR UPDATE")
    Long selectFavoriteCountForUpdate(@Param("videoId") long videoId);

    @Update("INSERT INTO video_stats (video_id, play_count, like_count, favorite_count, comment_count, updated_at) "
            + "VALUES (#{videoId}, 0, 0, 0, 0, NOW(3)) "
            + "ON DUPLICATE KEY UPDATE comment_count = GREATEST(0, CAST(comment_count AS SIGNED) + #{commentDelta}), updated_at = NOW(3)")
    int adjustCommentCount(@Param("videoId") long videoId, @Param("commentDelta") int commentDelta);
}
