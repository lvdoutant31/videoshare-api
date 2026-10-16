package com.yingliu.service.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.share.VideoShareInbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface VideoShareInboxMapper extends BaseMapper<VideoShareInbox> {

    /** 收件箱列表（按发送时间倒序） */
    @Select("SELECT * FROM video_share_inbox WHERE recipient_id = #{userId} ORDER BY sent_at DESC, id DESC")
    List<VideoShareInbox> selectInbox(@Param("userId") long userId);

    /** 最近转发对象（作为发送人的最近接收人，按发送时间倒序去重后取前 N） */
    @Select("SELECT i.* FROM video_share_inbox i "
            + "JOIN (SELECT MAX(id) AS id FROM video_share_inbox WHERE sender_id = #{senderId} "
            + "GROUP BY recipient_id ORDER BY MAX(sent_at) DESC LIMIT #{limit}) latest ON latest.id = i.id")
    List<VideoShareInbox> selectRecentRecipients(@Param("senderId") long senderId, @Param("limit") int limit);

    /** 同发送人同视频同接收人的最近送达记录（幂等去重判断） */
    @Select("SELECT * FROM video_share_inbox WHERE sender_id = #{senderId} AND recipient_id = #{recipientId} "
            + "AND video_id = #{videoId} AND status = 'DELIVERED' ORDER BY sent_at DESC LIMIT 1")
    VideoShareInbox selectRecentDelivered(@Param("senderId") long senderId, @Param("recipientId") long recipientId,
                                          @Param("videoId") long videoId);

    /** 标记已打开（幂等） */
    @Update("UPDATE video_share_inbox SET opened_at = NOW(3) "
            + "WHERE id = #{inboxId} AND recipient_id = #{userId} AND opened_at IS NULL")
    int markOpened(@Param("inboxId") long inboxId, @Param("userId") long userId);
}
