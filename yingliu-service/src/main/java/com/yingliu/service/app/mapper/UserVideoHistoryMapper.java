package com.yingliu.service.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.interaction.UserVideoHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserVideoHistoryMapper extends BaseMapper<UserVideoHistory> {

    /** 最近观看（按 video_id 倒序游标分页；video_id 稳定） */
    @Select("<script>SELECT * FROM user_video_history WHERE user_id = #{userId} "
            + "<if test='cursorVideoId != null'>AND video_id &lt; #{cursorVideoId} </if>"
            + "ORDER BY video_id DESC LIMIT #{limit}</script>")
    List<UserVideoHistory> selectRecent(@Param("userId") long userId,
                                        @Param("cursorVideoId") Long cursorVideoId, @Param("limit") int limit);

    /** 写入/更新观看历史（幂等 upsert） */
    @Update("INSERT INTO user_video_history (user_id, video_id, last_viewed_at, view_count, last_progress_ms) "
            + "VALUES (#{userId}, #{videoId}, NOW(3), 1, #{progressMs}) "
            + "ON DUPLICATE KEY UPDATE last_viewed_at = NOW(3), view_count = view_count + 1, "
            + "last_progress_ms = #{progressMs}")
    int upsert(@Param("userId") long userId, @Param("videoId") long videoId, @Param("progressMs") int progressMs);

    /** 清空个人历史 */
    @Update("DELETE FROM user_video_history WHERE user_id = #{userId}")
    int clearAll(@Param("userId") long userId);
}
