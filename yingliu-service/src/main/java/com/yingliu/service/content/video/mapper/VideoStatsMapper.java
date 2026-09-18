package com.yingliu.service.content.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.content.video.VideoStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VideoStatsMapper extends BaseMapper<VideoStats> {

    /** 互动计数原子增减（防并发丢更新）；不存在时插入初始行 */
    @Update("INSERT INTO video_stats (video_id, play_count, like_count, favorite_count, comment_count, updated_at) "
            + "VALUES (#{videoId}, 0, 0, 0, 0, NOW(3)) "
            + "ON DUPLICATE KEY UPDATE like_count = GREATEST(0, CAST(like_count AS SIGNED) + #{likeDelta}), updated_at = NOW(3)")
    int adjustLike(@Param("videoId") long videoId, @Param("likeDelta") int likeDelta);

    @Update("INSERT INTO video_stats (video_id, play_count, like_count, favorite_count, comment_count, updated_at) "
            + "VALUES (#{videoId}, 0, 0, 0, 0, NOW(3)) "
            + "ON DUPLICATE KEY UPDATE favorite_count = GREATEST(0, CAST(favorite_count AS SIGNED) + #{favoriteDelta}), updated_at = NOW(3)")
    int adjustFavorite(@Param("videoId") long videoId, @Param("favoriteDelta") int favoriteDelta);

    @Update("INSERT INTO video_stats (video_id, play_count, like_count, favorite_count, comment_count, updated_at) "
            + "VALUES (#{videoId}, 0, 0, 0, 0, NOW(3)) "
            + "ON DUPLICATE KEY UPDATE comment_count = GREATEST(0, CAST(comment_count AS SIGNED) + #{commentDelta}), updated_at = NOW(3)")
    int adjustCommentCount(@Param("videoId") long videoId, @Param("commentDelta") int commentDelta);
}
