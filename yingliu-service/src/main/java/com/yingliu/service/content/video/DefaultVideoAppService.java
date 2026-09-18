package com.yingliu.service.content.video;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.domain.content.tag.ContentTag;
import com.yingliu.domain.content.video.MediaResource;
import com.yingliu.domain.content.video.Video;
import com.yingliu.domain.content.video.VideoAuthor;
import com.yingliu.domain.content.video.VideoStats;
import com.yingliu.domain.content.video.VideoTagMap;
import com.yingliu.media.storage.MediaStorage;
import com.yingliu.service.app.AppInteractionService;
import com.yingliu.service.content.shared.CursorPage;
import com.yingliu.service.content.tag.mapper.ContentTagMapper;
import com.yingliu.service.content.video.mapper.MediaResourceMapper;
import com.yingliu.service.content.video.mapper.VideoAuthorMapper;
import com.yingliu.service.content.video.mapper.VideoMapper;
import com.yingliu.service.content.video.mapper.VideoStatsMapper;
import com.yingliu.service.content.video.mapper.VideoTagMapMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 面向手机端组装公开视频数据，查询内容并补充作者、标签和观看者状态。 */
@Service
@Profile("!test")
public class DefaultVideoAppService implements VideoAppService {
    private final VideoMapper videoMapper;
    private final VideoAuthorMapper authorMapper;
    private final MediaResourceMapper mediaMapper;
    private final VideoStatsMapper statsMapper;
    private final VideoTagMapMapper tagMapMapper;
    private final ContentTagMapper tagMapper;
    private final MediaStorage mediaStorage;
    private final AppInteractionService interactionService;

    public DefaultVideoAppService(VideoMapper videoMapper, VideoAuthorMapper authorMapper,
                                  MediaResourceMapper mediaMapper, VideoStatsMapper statsMapper,
                                  VideoTagMapMapper tagMapMapper, ContentTagMapper tagMapper,
                                  MediaStorage mediaStorage, AppInteractionService interactionService) {
        this.videoMapper = videoMapper;
        this.authorMapper = authorMapper;
        this.mediaMapper = mediaMapper;
        this.statsMapper = statsMapper;
        this.tagMapMapper = tagMapMapper;
        this.tagMapper = tagMapper;
        this.mediaStorage = mediaStorage;
        this.interactionService = interactionService;
    }

    @Override
    public CursorPage<FeedItem> recommend(String cursor, int limit, Long viewerId) {
        Cursor value = decodeCursor(cursor);
        List<Video> rows = videoMapper.selectPublicFeed(value == null ? null : value.publishedAt(),
                value == null ? null : value.videoId(), limit + 1);
        return assemblePage(rows, limit, viewerId);
    }

    @Override
    public CursorPage<FeedItem> following(String cursor, int limit, long viewerId) {
        Cursor value = decodeCursor(cursor);
        List<Video> rows = videoMapper.selectFollowingFeed(viewerId, value == null ? null : value.publishedAt(),
                value == null ? null : value.videoId(), limit + 1);
        return assemblePage(rows, limit, viewerId);
    }

    private CursorPage<FeedItem> assemblePage(List<Video> rows, int limit, Long viewerId) {
        boolean hasNext = rows.size() > limit;
        List<Video> videos = hasNext ? rows.subList(0, limit) : rows;
        Map<Long, VideoAuthor> authors = authors(videos.stream().map(Video::getAuthorId).toList());
        Map<Long, MediaResource> originals = originals(videos);
        Map<Long, MediaResource> covers = covers(videos);
        Map<Long, VideoStats> stats = stats(videos.stream().map(Video::getId).toList());
        Map<Long, List<Tag>> tags = tags(videos.stream().map(Video::getId).toList());
        List<Long> videoIds = videos.stream().map(Video::getId).toList();
        Set<Long> likedIds = viewerId == null ? Set.of() : interactionService.likedVideoIds(viewerId, videoIds);
        Set<Long> favoritedIds = viewerId == null ? Set.of() : interactionService.favoritedVideoIds(viewerId, videoIds);
        Set<Long> followingIds = viewerId == null ? Set.of()
                : interactionService.followingIds(viewerId, videos.stream().map(Video::getAuthorId).toList());
        List<FeedItem> items = videos.stream().map(video -> feedItem(video,
                        video.getAuthorId() != null ? authors.getOrDefault(video.getAuthorId(), null) : null,
                        video.getOriginalMediaId() != null ? originals.getOrDefault(video.getOriginalMediaId(), null) : null,
                        video.getCoverMediaId() != null ? covers.getOrDefault(video.getCoverMediaId(), null) : null,
                        stats.getOrDefault(video.getId(), null),
                        tags.getOrDefault(video.getId(), List.of()),
                        new ViewerState(likedIds.contains(video.getId()), favoritedIds.contains(video.getId())),
                        followingIds.contains(video.getAuthorId())))
                .toList();
        String nextCursor = hasNext ? encodeCursor(videos.get(videos.size() - 1)) : null;
        return new CursorPage<>(items, nextCursor);
    }

    @Override
    public VideoDetail getPublicVideo(long videoId, Long viewerId) {
        Video video = videoMapper.selectPublicById(videoId);
        if (video == null) throw notFound();
        VideoAuthor author = authorMapper.selectById(video.getAuthorId());
        if (author == null || !"NORMAL".equals(author.getStatus())) throw notFound();
        MediaResource original = mediaMapper.selectById(video.getOriginalMediaId());
        if (original == null || !"VIDEO_ORIGINAL".equals(original.getResourceType()) || !"READY".equals(original.getStorageState())) {
            throw notFound();
        }
        MediaResource cover = cover(video.getCoverMediaId());
        VideoStats stats = statsMapper.selectById(video.getId());
        boolean liked = viewerId != null && interactionService.likedVideoIds(viewerId, List.of(video.getId())).contains(video.getId());
        boolean favorited = viewerId != null && interactionService.favoritedVideoIds(viewerId, List.of(video.getId())).contains(video.getId());
        boolean following = viewerId != null && interactionService.isFollowing(viewerId, video.getAuthorId());
        return new VideoDetail(video.getId(), author.getId(), author.getNickname(), video.getTitle(), video.getDescription(),
                original.getDurationMs(),
                url(original),
                cover(cover), tags(List.of(video.getId())).getOrDefault(video.getId(), List.of()), stats(stats),
                video.getPublishedAt(), new ViewerState(liked, favorited), following);
    }

    private FeedItem feedItem(Video video, VideoAuthor author, MediaResource original, MediaResource cover,
                              VideoStats stats, List<Tag> tags, ViewerState state, boolean authorFollowing) {
        String videoUrl = url(original);
        return new FeedItem(video.getId(), author.getId(), author.getNickname(), video.getTitle(), video.getDescription(),
                original == null ? null : original.getDurationMs(), videoUrl, cover(cover), tags, stats(stats),
                video.getPublishedAt(), state, authorFollowing);
    }

    private Map<Long, VideoAuthor> authors(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return authorMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(VideoAuthor::getId, Function.identity()));
    }

    private Map<Long, MediaResource> originals(List<Video> videos) {
        List<Long> ids = videos.stream().map(Video::getOriginalMediaId).toList();
        if (ids.isEmpty()) return Map.of();
        return mediaMapper.selectBatchIds(ids).stream()
                .filter(media -> "VIDEO_ORIGINAL".equals(media.getResourceType()) && "READY".equals(media.getStorageState()))
                .collect(Collectors.toMap(MediaResource::getId, Function.identity()));
    }

    private Map<Long, MediaResource> covers(List<Video> videos) {
        List<Long> ids = videos.stream().map(Video::getCoverMediaId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return Map.of();
        return mediaMapper.selectBatchIds(ids).stream()
                .filter(media -> "COVER".equals(media.getResourceType()) && "READY".equals(media.getStorageState()))
                .collect(Collectors.toMap(MediaResource::getId, Function.identity()));
    }

    private MediaResource cover(Long id) {
        if (id == null) return null;
        MediaResource media = mediaMapper.selectById(id);
        return media != null && "COVER".equals(media.getResourceType()) && "READY".equals(media.getStorageState()) ? media : null;
    }

    private Map<Long, VideoStats> stats(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return statsMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(VideoStats::getVideoId, Function.identity()));
    }

    private Map<Long, List<Tag>> tags(Collection<Long> videoIds) {
        if (videoIds.isEmpty()) return Map.of();
        Map<Long, List<Long>> tagIds = tagMapMapper.selectList(new LambdaQueryWrapper<VideoTagMap>()
                        .in(VideoTagMap::getVideoId, videoIds))
                .stream().collect(Collectors.groupingBy(VideoTagMap::getVideoId,
                        LinkedHashMap::new, Collectors.mapping(VideoTagMap::getTagId, Collectors.toList())));
        List<Long> allTagIds = tagIds.values().stream().flatMap(List::stream).distinct().toList();
        Map<Long, ContentTag> tags = allTagIds.isEmpty() ? Map.of() : tagMapper.selectBatchIds(allTagIds).stream()
                .filter(tag -> "ENABLED".equals(tag.getStatus()) && tag.getMergedToId() == null)
                .collect(Collectors.toMap(ContentTag::getId, Function.identity()));
        return tagIds.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue().stream().map(tags::get).filter(java.util.Objects::nonNull)
                        .map(tag -> new Tag(tag.getId(), tag.getName())).toList()));
    }

    private Cover cover(MediaResource media) {
        return media == null ? null : new Cover(media.getId(), media.getContentType(), media.getWidth(), media.getHeight(),
                url(media));
    }

    private String url(MediaResource media) {
        if (media == null) return null;
        // Rebuild the public URL from the storage key when possible. Persisted
        // absolute URLs may contain a development-only host such as localhost.
        return media.getRelativePath() != null ? mediaStorage.getUrl(media.getRelativePath())
                : media.getUrl();
    }

    private Stats stats(VideoStats value) {
        return value == null ? new Stats(0, 0, 0, 0) : new Stats(value.getPlayCount(), value.getLikeCount(),
                value.getFavoriteCount(), value.getCommentCount());
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            LocalDateTime publishedAt = LocalDateTime.parse(parts[0]);
            long videoId = Long.parseLong(parts[1]);
            if (videoId <= 0) throw new IllegalArgumentException();
            return new Cursor(publishedAt, videoId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("INVALID_CURSOR", HttpStatus.UNPROCESSABLE_ENTITY, "分页游标不合法");
        }
    }

    private String encodeCursor(Video video) {
        String value = video.getPublishedAt() + "|" + video.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private BusinessException notFound() {
        return new BusinessException("VIDEO_NOT_FOUND", HttpStatus.NOT_FOUND, "视频不存在");
    }

    private record Cursor(LocalDateTime publishedAt, long videoId) { }
}
