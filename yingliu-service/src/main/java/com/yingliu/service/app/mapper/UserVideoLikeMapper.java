package com.yingliu.service.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.interaction.UserVideoLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserVideoLikeMapper extends BaseMapper<UserVideoLike> {
    @Select("<script>SELECT v.id FROM user_video_like l JOIN video v ON v.id = l.video_id WHERE l.user_id = #{userId} AND "
            + com.yingliu.service.content.video.mapper.VideoMapper.PUBLIC_VIDEO_WHERE
            + "<if test='cursorId != null'>AND v.id &lt; #{cursorId} </if>ORDER BY v.id DESC LIMIT #{limit}</script>")
    java.util.List<Long> selectPublicIds(@Param("userId") long userId, @Param("cursorId") Long cursorId, @Param("limit") int limit);

    @Insert("INSERT IGNORE INTO user_video_like (user_id, video_id, created_at) "
            + "VALUES (#{userId}, #{videoId}, NOW(3))")
    int insertIfAbsent(@Param("userId") long userId, @Param("videoId") long videoId);

    @Delete("DELETE FROM user_video_like WHERE user_id = #{userId} AND video_id = #{videoId}")
    int deleteByUserAndVideo(@Param("userId") long userId, @Param("videoId") long videoId);

    @Select("SELECT COUNT(*) FROM user_video_like WHERE video_id = #{videoId}")
    long countByVideo(@Param("videoId") long videoId);
}
