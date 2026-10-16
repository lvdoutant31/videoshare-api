package com.yingliu.service.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.comment.VideoComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface VideoCommentMapper extends BaseMapper<VideoComment> {

    /** 一级评论列表：综合热度（hot_score DESC, id DESC）或最新（created_at DESC, id DESC），游标 id 分页 */
    @Select("<script>SELECT * FROM video_comment WHERE video_id = #{videoId} AND root_comment_id = 0 "
            + "AND status = 'NORMAL' AND deleted_at IS NULL "
            + "<if test='sort == \"LATEST\"'>ORDER BY created_at DESC, id DESC</if>"
            + "<if test='sort != \"LATEST\"'>ORDER BY hot_score DESC, id DESC</if>"
            + "<if test='cursorId != null'>AND id &lt; #{cursorId}</if> LIMIT #{limit}</script>")
    List<VideoComment> selectRootPage(@Param("videoId") long videoId, @Param("sort") String sort,
                                      @Param("cursorId") Long cursorId, @Param("limit") int limit);

    /** 一级评论下按时间正序的回复 */
    @Select("SELECT * FROM video_comment WHERE root_comment_id = #{rootCommentId} AND status = 'NORMAL' "
            + "AND deleted_at IS NULL ORDER BY created_at ASC, id ASC")
    List<VideoComment> selectReplies(@Param("rootCommentId") long rootCommentId);

    /** 视频可见的一级评论数（公开计数） */
    @Select("SELECT COUNT(*) FROM video_comment WHERE video_id = #{videoId} AND root_comment_id = 0 "
            + "AND status = 'NORMAL' AND deleted_at IS NULL")
    long countVisibleRoot(@Param("videoId") long videoId);

    /** 发布回复时回复数 +1；幂等：仅当评论仍可见 */
    @Update("UPDATE video_comment SET reply_count = reply_count + 1, updated_at = NOW(3) "
            + "WHERE id = #{rootCommentId} AND status = 'NORMAL' AND deleted_at IS NULL")
    int incrementReplyCount(@Param("rootCommentId") long rootCommentId);

    @Update("UPDATE video_comment SET "
            + "like_count = GREATEST(0, CAST(like_count AS SIGNED) + #{delta}), "
            + "hot_score = hot_score + #{bonus}, updated_at = NOW(3) "
            + "WHERE id = #{commentId} AND status = 'NORMAL' AND deleted_at IS NULL")
    int adjustLikeCountAndHotScore(@Param("commentId") long commentId,
                                   @Param("delta") int delta,
                                   @Param("bonus") int bonus);

    /** 当前读，避免默认 REPEATABLE READ 下并发幂等请求返回旧快照。 */
    @Select("SELECT like_count FROM video_comment WHERE id = #{commentId} FOR UPDATE")
    long selectLikeCountForUpdate(@Param("commentId") long commentId);

    /** 删除评论（逻辑删除）；一级评论删除后其回复一并隐藏 */
    @Update("UPDATE video_comment SET status = 'DELETED', deleted_at = NOW(3), deleted_by = #{userId}, "
            + "delete_reason = 'USER_DELETED', updated_at = NOW(3) WHERE id = #{commentId} AND user_id = #{userId} AND deleted_at IS NULL")
    int deleteOwned(@Param("commentId") long commentId, @Param("userId") long userId);

    @Update("UPDATE video_comment SET status = 'DELETED', deleted_at = NOW(3), deleted_by = #{userId}, "
            + "delete_reason = 'USER_DELETED', updated_at = NOW(3) "
            + "WHERE root_comment_id = #{rootCommentId} AND deleted_at IS NULL")
    int hideRepliesOf(@Param("rootCommentId") long rootCommentId, @Param("userId") long userId);
}
