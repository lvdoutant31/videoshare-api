package com.yingliu.service.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.domain.content.video.Video;
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
        Video video = requirePublicVideo(videoId);
        int changed = active
                ? likeMapper.insertIfAbsent(userId, videoId)
                : likeMapper.deleteByUserAndVideo(userId, videoId);
        if (changed == 1) {
            int delta = active ? 1 : -1;
            statsMapper.adjustLike(videoId, delta);
            userMapper.adjustLikeReceivedCount(video.getAuthorId(), delta);
        }
        return new TargetState(active, currentLikeCount(videoId));
    }

    @Override
    @Transactional
    public TargetState setVideoFavorite(long userId, long videoId, boolean active) {
        requirePublicVideo(videoId);
        int changed = active
                ? favoriteMapper.insertIfAbsent(userId, videoId)
                : favoriteMapper.deleteByUserAndVideo(userId, videoId);
        if (changed == 1) {
            statsMapper.adjustFavorite(videoId, active ? 1 : -1);
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
        return toVideoIdPage(favoriteMapper.selectPublicIds(userId, cursorId, pageSize + 1), pageSize);
    }

    @Override
    public CursorPage<String> listLikeVideoIds(long userId, String cursor, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), MAX_LIST_LIMIT);
        Long cursorId = parseCursor(cursor);
        return toVideoIdPage(likeMapper.selectPublicIds(userId, cursorId, pageSize + 1), pageSize);
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
    public AuthorProfileView getAuthorProfile(long userId, Long viewerId) {
        var user = userMapper.selectById(userId);
        if (user == null || Objects.equals(user.getDeleted(), 1) || !"NORMAL".equals(user.getStatus())) {
            throw new BusinessException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "用户不存在");
        }
        return new AuthorProfileView(String.valueOf(user.getId()), user.getNickname(), avatarUrl(user.getAvatarMediaId()), user.getBio(),
                nullSafe(user.getFollowingCount()), nullSafe(user.getFollowerCount()), nullSafe(user.getLikeReceivedCount()),
                viewerId != null && isFollowing(viewerId, userId), videoMapper.countPublicByAuthor(userId));
    }

    @Override
    public CursorPage<AuthorSearchView> searchAuthors(String keyword, String cursor, int limit, Long viewerId) {
        String query = keyword == null ? "" : keyword.trim();
        if (query.isEmpty()) return new CursorPage<>(List.of(), null);
        if (query.length() > 60) throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "搜索关键词不能超过 60 字");
        int pageSize = Math.min(Math.max(limit, 1), MAX_LIST_LIMIT);
        var rows = userMapper.searchAuthors(query, parseCursor(cursor), pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        var page = hasNext ? rows.subList(0, pageSize) : rows;
        if (page.isEmpty()) return new CursorPage<>(List.of(), null);
        var ids = page.stream().map(com.yingliu.domain.user.AppUser::getId).toList();
        var counts = videoMapper.countPublicByAuthors(ids).stream().collect(Collectors.toMap(VideoMapper.AuthorContentCount::authorId, VideoMapper.AuthorContentCount::total));
        var avatarIds = page.stream().map(com.yingliu.domain.user.AppUser::getAvatarMediaId).filter(Objects::nonNull).distinct().toList();
        java.util.Map<Long, String> avatars = new java.util.HashMap<>();
        if (!avatarIds.isEmpty()) mediaMapper.selectBatchIds(avatarIds).forEach(media -> {
            if (media.getRelativePath() != null) avatars.put(media.getId(), mediaStorage.getUrl(media.getRelativePath()));
        });
        Set<Long> followed = viewerId == null ? Set.of() : followingIds(viewerId, ids);
        var items = page.stream().map(user -> new AuthorSearchView(String.valueOf(user.getId()), user.getNickname(),
                avatars.get(user.getAvatarMediaId()), counts.getOrDefault(user.getId(), 0L), followed.contains(user.getId()))).toList();
        return new CursorPage<>(items, hasNext ? String.valueOf(page.get(page.size() - 1).getId()) : null);
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

    private Video requirePublicVideo(long videoId) {
        Video video = videoMapper.selectPublicById(videoId);
        if (video == null) {
            throw new BusinessException("VIDEO_NOT_FOUND", HttpStatus.NOT_FOUND, "视频不存在或不可访问");
        }
        return video;
    }

    private long currentFavoriteCount(long videoId) {
        Long count = statsMapper.selectFavoriteCountForUpdate(videoId);
        return count == null ? 0 : count;
    }

    private long currentLikeCount(long videoId) {
        Long count = statsMapper.selectLikeCountForUpdate(videoId);
        if (count == null || count == 0) {
            long authoritativeCount = likeMapper.countByVideo(videoId);
            if (authoritativeCount > 0) {
                statsMapper.setLikeCount(videoId, authoritativeCount);
                return authoritativeCount;
            }
        }
        return count == null ? 0 : count;
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
