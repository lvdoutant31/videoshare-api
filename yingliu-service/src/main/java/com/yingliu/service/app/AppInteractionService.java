package com.yingliu.service.app;

import com.yingliu.service.content.shared.CursorPage;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * App 域互动（API §2.3）：点赞/收藏/关注（目标状态幂等）+ 个人列表 + 作者主页。
 * 注意：对外 ID 全部字符串化（契约要求，避免 JS 精度丢失）。
 */
public interface AppInteractionService {

    /** 设置视频点赞目标状态；返回服务端权威计数 */
    TargetState setVideoLike(long userId, long videoId, boolean active);

    /** 设置视频收藏目标状态 */
    TargetState setVideoFavorite(long userId, long videoId, boolean active);

    /** 设置关注目标状态（禁止自关注） */
    TargetState setFollow(long userId, long followeeId, boolean active);

    /** 我收藏的视频 ID（按 video_id 倒序游标分页；对外返回字符串 id） */
    CursorPage<String> listFavoriteVideoIds(long userId, String cursor, int limit);

    /** 我点赞的视频 ID */
    CursorPage<String> listLikeVideoIds(long userId, String cursor, int limit);

    /** 我关注的用户列表 */
    List<UserBriefView> listFollowing(long userId);

    /** 我的粉丝用户列表 */
    List<UserBriefView> listFollowers(long userId);

    /** 作者公开资料（含统计和当前访问者的关注状态）；viewerId 可空（游客） */
    AuthorProfileView getAuthorProfile(long userId, Long viewerId);

    /** 作者公开视频 ID（游标分页；对外返回字符串 id） */
    CursorPage<String> listAuthorVideoIds(long authorId, String cursor, int limit);

    CursorPage<AuthorSearchView> searchAuthors(String keyword, String cursor, int limit, Long viewerId);

    /** 是否已关注 */
    boolean isFollowing(long followerId, long followeeId);

    /** 批量查询：followerId 已关注的 followeeIds 子集 */
    Set<Long> followingIds(long followerId, Collection<Long> followeeIds);

    /** 批量查询：视频是否被当前用户点赞/收藏 */
    Set<Long> likedVideoIds(long userId, Collection<Long> videoIds);

    Set<Long> favoritedVideoIds(long userId, Collection<Long> videoIds);

    record TargetState(boolean active, long count) { }

    record UserBriefView(String id, String nickname, String avatarUrl, boolean following) { }

    record AuthorProfileView(String id, String nickname, String avatarUrl, String bio,
                             long followingCount, long followerCount, long likeReceivedCount, boolean following, long worksCount) { }

    record AuthorSearchView(String id, String nickname, String avatarUrl, long worksCount, boolean following) { }
}
