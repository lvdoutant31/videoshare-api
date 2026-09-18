package com.yingliu.service.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.domain.content.video.Video;
import com.yingliu.domain.content.video.VideoStats;
import com.yingliu.domain.interaction.UserFollow;
import com.yingliu.domain.interaction.UserVideoFavorite;
import com.yingliu.domain.interaction.UserVideoLike;
import com.yingliu.service.app.mapper.AppUserMapper;
import com.yingliu.service.app.mapper.UserFollowMapper;
import com.yingliu.service.app.mapper.UserVideoFavoriteMapper;
import com.yingliu.service.app.mapper.UserVideoLikeMapper;
import com.yingliu.service.content.shared.CursorPage;
import com.yingliu.service.content.video.mapper.VideoMapper;
import com.yingliu.service.content.video.mapper.VideoStatsMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Profile("!test")
public class DefaultAppInteractionService implements AppInteractionService {
    private static final int MAX_LIST_LIMIT = 50;

    private final UserVideoLikeMapper likeMapper;
    private final UserVideoFavoriteMapper favoriteMapper;
    private final UserFollowMapper followMapper;
    private final VideoMapper videoMapper;
    private final VideoStatsMapper statsMapper;
    private final AppUserMapper userMapper;
    private final com.yingliu.service.content.video.mapper.MediaResourceMapper mediaMapper;
    private final com.yingliu.media.storage.MediaStorage mediaStorage;

    public DefaultAppInteractionService(UserVideoLikeMapper likeMapper,
                                        UserVideoFavoriteMapper favoriteMapper,
                                        UserFollowMapper followMapper,
                                        VideoMapper videoMapper,
                                        VideoStatsMapper statsMapper,
                                        AppUserMapper userMapper,
                                        com.yingliu.service.content.video.mapper.MediaResourceMapper mediaMapper,
                                        com.yingliu.media.storage.MediaStorage mediaStorage) {
        this.likeMapper = likeMapper;
        this.favoriteMapper = favoriteMapper;
        this.followMapper = followMapper;
        this.videoMapper = videoMapper;
        this.statsMapper = statsMapper;
        this.userMapper = userMapper;
        this.mediaMapper = mediaMapper;
        this.mediaStorage = mediaStorage;
    }

    @Override
    @Transactional
    public TargetState setVideoLike(long userId, long videoId, boolean active) {
        requirePublicVideo(videoId);
        boolean existed = likeMapper.selectCount(new LambdaQueryWrapper<UserVideoLike>()
                .eq(UserVideoLike::getUserId, userId).eq(UserVideoLike::getVideoId, videoId)) > 0;
        if (active && !existed) {
            insertLike(userId, videoId);
            statsMapper.adjustLike(videoId, 1);
        } else if (!active && existed) {
            likeMapper.delete(new LambdaQueryWrapper<UserVideoLike>()
                    .eq(UserVideoLike::getUserId, userId).eq(UserVideoLike::getVideoId, videoId));
            statsMapper.adjustLike(videoId, -1);
        }
        return new TargetState(active, currentLikeCount(videoId));
    }

    @Override
    @Transactional
    public TargetState setVideoFavorite(long userId, long videoId, boolean active) {
        requirePublicVideo(videoId);
        boolean existed = favoriteMapper.selectCount(new LambdaQueryWrapper<UserVideoFavorite>()
                .eq(UserVideoFavorite::getUserId, userId).eq(UserVideoFavorite::getVideoId, videoId)) > 0;
        if (active && !existed) {
            insertFavorite(userId, videoId);
            statsMapper.adjustFavorite(videoId, 1);
        } else if (!active && existed) {
            favoriteMapper.delete(new LambdaQueryWrapper<UserVideoFavorite>()
                    .eq(UserVideoFavorite::getUserId, userId).eq(UserVideoFavorite::getVideoId, videoId));
            statsMapper.adjustFavorite(videoId, -1);
        }
        return new TargetState(active, currentFavoriteCount(videoId));
    }

    @Override
    @Transactional
    public TargetState setFollow(long userId, long followeeId, boolean active) {
        if (userId == followeeId) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "不能关注自己");
        }
        if (userMapper.countNormal(followeeId) == 0) {
            throw new BusinessException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "用户不存在");
        }
        boolean existed = followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId).eq(UserFollow::getFolloweeId, followeeId)) > 0;
        if (active && !existed) {
            insertFollow(userId, followeeId);
            userMapper.adjustFollowingCount(userId, 1);
            userMapper.adjustFollowerCount(followeeId, 1);
        } else if (!active && existed) {
            followMapper.delete(new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getFollowerId, userId).eq(UserFollow::getFolloweeId, followeeId));
            userMapper.adjustFollowingCount(userId, -1);
            userMapper.adjustFollowerCount(followeeId, -1);
        }
        return new TargetState(active, currentFollowerCount(followeeId));
    }

    @Override
    public CursorPage<String> listFavoriteVideoIds(long userId, String cursor, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), MAX_LIST_LIMIT);
        Long cursorId = parseCursor(cursor);
        List<UserVideoFavorite> rows = favoriteMapper.selectList(new LambdaQueryWrapper<UserVideoFavorite>()
                .eq(UserVideoFavorite::getUserId, userId)
                .lt(cursorId != null, UserVideoFavorite::getVideoId, cursorId)
                .orderByDesc(UserVideoFavorite::getVideoId)
                .last("LIMIT " + (pageSize + 1)));
        return toVideoIdPage(rows.stream().map(UserVideoFavorite::getVideoId).toList(), pageSize);
    }

    @Override
    public CursorPage<String> listLikeVideoIds(long userId, String cursor, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), MAX_LIST_LIMIT);
        Long cursorId = parseCursor(cursor);
        List<UserVideoLike> rows = likeMapper.selectList(new LambdaQueryWrapper<UserVideoLike>()
                .eq(UserVideoLike::getUserId, userId)
                .lt(cursorId != null, UserVideoLike::getVideoId, cursorId)
                .orderByDesc(UserVideoLike::getVideoId)
                .last("LIMIT " + (pageSize + 1)));
        return toVideoIdPage(rows.stream().map(UserVideoLike::getVideoId).toList(), pageSize);
    }

    @Override
    public List<UserBriefView> listFollowing(long userId) {
        List<Long> ids = followMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, userId)
                        .orderByDesc(UserFollow::getCreatedAt))
                .stream().map(UserFollow::getFolloweeId).toList();
        return userBriefs(ids, userId);
    }

    @Override
    public List<UserBriefView> listFollowers(long userId) {
        List<Long> ids = followMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFolloweeId, userId)
                        .orderByDesc(UserFollow::getCreatedAt))
                .stream().map(UserFollow::getFollowerId).toList();
        return userBriefs(ids, userId);
    }

    @Override
    public AuthorProfileView getAuthorProfile(long userId) {
        var user = userMapper.selectById(userId);
        if (user == null || Objects.equals(user.getDeleted(), 1)) {
            throw new BusinessException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "用户不存在");
        }
        return new AuthorProfileView(String.valueOf(user.getId()), user.getNickname(), avatarUrl(user.getAvatarMediaId()), user.getBio(),
                nullSafe(user.getFollowingCount()), nullSafe(user.getFollowerCount()), nullSafe(user.getLikeReceivedCount()));
    }

    @Override
    public CursorPage<String> listAuthorVideoIds(long authorId, String cursor, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), MAX_LIST_LIMIT);
        Long cursorId = parseCursor(cursor);
        List<Long> rows = videoMapper.selectPublicIdsByAuthor(authorId, cursorId, pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        List<Long> page = hasNext ? rows.subList(0, pageSize) : rows;
        List<String> ids = page.stream().map(String::valueOf).toList();
        String nextCursor = ids.isEmpty() ? null : String.valueOf(ids.get(ids.size() - 1));
        return new CursorPage<>(ids, hasNext ? nextCursor : null);
    }

    private List<UserBriefView> userBriefs(List<Long> userIds, long viewerId) {
        if (userIds.isEmpty()) return List.of();
        var users = userMapper.selectBatchIds(userIds);
        Set<Long> following = followingIds(viewerId, userIds);
        return users.stream()
                .map(u -> new UserBriefView(String.valueOf(u.getId()), u.getNickname(), avatarUrl(u.getAvatarMediaId()), following.contains(u.getId())))
                .toList();
    }

    private String avatarUrl(Long mediaId) {
        if (mediaId == null) return null;
        var media = mediaMapper.selectById(mediaId);
        return media != null && media.getRelativePath() != null ? mediaStorage.getUrl(media.getRelativePath()) : null;
    }

    private long nullSafe(Long value) {
        return value == null ? 0 : value;
    }

    @Override
    public boolean isFollowing(long followerId, long followeeId) {
        return followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId).eq(UserFollow::getFolloweeId, followeeId)) > 0;
    }

    @Override
    public Set<Long> followingIds(long followerId, Collection<Long> followeeIds) {
        if (followeeIds.isEmpty()) return Set.of();
        return followMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, followerId)
                        .in(UserFollow::getFolloweeId, followeeIds))
                .stream().map(UserFollow::getFolloweeId).collect(Collectors.toSet());
    }

    @Override
    public Set<Long> likedVideoIds(long userId, Collection<Long> videoIds) {
        if (videoIds.isEmpty()) return Set.of();
        return likeMapper.selectList(new LambdaQueryWrapper<UserVideoLike>()
                        .eq(UserVideoLike::getUserId, userId)
                        .in(UserVideoLike::getVideoId, videoIds))
                .stream().map(UserVideoLike::getVideoId).collect(Collectors.toSet());
    }

    @Override
    public Set<Long> favoritedVideoIds(long userId, Collection<Long> videoIds) {
        if (videoIds.isEmpty()) return Set.of();
        return favoriteMapper.selectList(new LambdaQueryWrapper<UserVideoFavorite>()
                        .eq(UserVideoFavorite::getUserId, userId)
                        .in(UserVideoFavorite::getVideoId, videoIds))
                .stream().map(UserVideoFavorite::getVideoId).collect(Collectors.toSet());
    }

    private void insertLike(long userId, long videoId) {
        UserVideoLike like = new UserVideoLike();
        like.setUserId(userId);
        like.setVideoId(videoId);
        like.setCreatedAt(LocalDateTime.now());
        try {
            likeMapper.insert(like);
        } catch (DuplicateKeyException ignored) {
            // 唯一约束兜底：并发重复点赞视为已存在
        }
    }

    private void insertFavorite(long userId, long videoId) {
        UserVideoFavorite favorite = new UserVideoFavorite();
        favorite.setUserId(userId);
        favorite.setVideoId(videoId);
        favorite.setCreatedAt(LocalDateTime.now());
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException ignored) {
            // 唯一约束兜底
        }
    }

    private void insertFollow(long userId, long followeeId) {
        UserFollow follow = new UserFollow();
        follow.setFollowerId(userId);
        follow.setFolloweeId(followeeId);
        follow.setCreatedAt(LocalDateTime.now());
        try {
            followMapper.insert(follow);
        } catch (DuplicateKeyException ignored) {
            // 唯一约束兜底
        }
    }

    private void requirePublicVideo(long videoId) {
        Video video = videoMapper.selectPublicById(videoId);
        if (video == null) {
            throw new BusinessException("VIDEO_NOT_FOUND", HttpStatus.NOT_FOUND, "视频不存在或不可访问");
        }
    }

    private long currentLikeCount(long videoId) {
        VideoStats stats = statsMapper.selectById(videoId);
        return stats == null || stats.getLikeCount() == null ? 0 : stats.getLikeCount();
    }

    private long currentFavoriteCount(long videoId) {
        VideoStats stats = statsMapper.selectById(videoId);
        return stats == null || stats.getFavoriteCount() == null ? 0 : stats.getFavoriteCount();
    }

    private long currentFollowerCount(long userId) {
        var user = userMapper.selectById(userId);
        return user == null || user.getFollowerCount() == null ? 0 : user.getFollowerCount();
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            long id = Long.parseLong(cursor);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            throw new BusinessException("INVALID_CURSOR", HttpStatus.UNPROCESSABLE_ENTITY, "分页游标不合法");
        }
    }

    private CursorPage<String> toVideoIdPage(List<Long> ids, int pageSize) {
        boolean hasNext = ids.size() > pageSize;
        List<Long> page = hasNext ? ids.subList(0, pageSize) : ids;
        List<String> stringIds = page.stream().map(String::valueOf).toList();
        String nextCursor = stringIds.isEmpty() ? null : String.valueOf(stringIds.get(stringIds.size() - 1));
        return new CursorPage<>(stringIds, hasNext ? nextCursor : null);
    }
}
