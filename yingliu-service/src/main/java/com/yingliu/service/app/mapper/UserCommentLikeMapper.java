package com.yingliu.service.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.interaction.UserCommentLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserCommentLikeMapper extends BaseMapper<UserCommentLike> {
    @Insert("INSERT IGNORE INTO user_comment_like (user_id, comment_id, created_at) "
            + "VALUES (#{userId}, #{commentId}, NOW(3))")
    int insertIfAbsent(@Param("userId") long userId, @Param("commentId") long commentId);

    @Delete("DELETE FROM user_comment_like WHERE user_id = #{userId} AND comment_id = #{commentId}")
    int deleteByUserAndComment(@Param("userId") long userId, @Param("commentId") long commentId);
}
