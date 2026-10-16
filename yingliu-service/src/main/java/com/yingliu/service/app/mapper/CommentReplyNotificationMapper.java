package com.yingliu.service.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.comment.CommentReplyNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CommentReplyNotificationMapper extends BaseMapper<CommentReplyNotification> {
    @Select("<script>SELECT * FROM comment_reply_notification WHERE recipient_user_id = #{userId} "
            + "<if test='cursorId != null'>AND id &lt; #{cursorId} </if>"
            + "ORDER BY created_at DESC, id DESC LIMIT #{limit}</script>")
    List<CommentReplyNotification> selectPage(@Param("userId") long userId,
                                              @Param("cursorId") Long cursorId,
                                              @Param("limit") int limit);

    @Update("UPDATE comment_reply_notification SET read_at = COALESCE(read_at, NOW(3)) "
            + "WHERE id = #{notificationId} AND recipient_user_id = #{userId}")
    int markRead(@Param("notificationId") long notificationId, @Param("userId") long userId);
}
