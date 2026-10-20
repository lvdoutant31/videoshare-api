package com.yingliu.service.content.video;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.domain.content.tag.ContentTag;
import com.yingliu.domain.content.category.ContentCategory;
import com.yingliu.domain.content.video.MediaResource;
import com.yingliu.domain.content.video.Video;
import com.yingliu.domain.content.video.VideoAuthor;
import com.yingliu.domain.content.video.VideoStats;
import com.yingliu.domain.content.video.VideoTagMap;
import com.yingliu.domain.content.video.VideoCategoryMap;
import com.yingliu.media.storage.MediaStorage;
import com.yingliu.service.app.AppInteractionService;
import com.yingliu.service.content.shared.CursorPage;
import com.yingliu.service.content.tag.mapper.ContentTagMapper;
import com.yingliu.service.content.category.mapper.ContentCategoryMapper;
import com.yingliu.service.content.video.mapper.MediaResourceMapper;
import com.yingliu.service.content.video.mapper.VideoAuthorMapper;
import com.yingliu.service.content.video.mapper.VideoMapper;
import com.yingliu.service.content.video.mapper.VideoStatsMapper;
import com.yingliu.service.content.video.mapper.VideoTagMapMapper;
import com.yingliu.service.content.video.mapper.VideoCategoryMapMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final VideoCategoryMapMapper categoryMapMapper;
    private final ContentCategoryMapper categoryMapper;
    private final MediaStorage mediaStorage;
    private final AppInteractionService interactionService;
    private final VideoTranscodeService transcodeService;

    public DefaultVideoAppService(VideoMapper videoMapper, VideoAuthorMapper authorMapper,
                                  MediaResourceMapper mediaMapper, VideoStatsMapper statsMapper,
                                  VideoTagMapMapper tagMapMapper, ContentTagMapper tagMapper,
                                  VideoCategoryMapMapper categoryMapMapper, ContentCategoryMapper categoryMapper,
                                  MediaStorage mediaStorage, AppInteractionService interactionService,
                                  VideoTranscodeService transcodeService) {
        this.videoMapper = videoMapper;
        this.authorMapper = authorMapper;
        this.mediaMapper = mediaMapper;
        this.statsMapper = statsMapper;
        this.tagMapMapper = tagMapMapper;
        this.tagMapper = tagMapper;
        this.categoryMapMapper = categoryMapMapper;
        this.categoryMapper = categoryMapper;
        this.mediaStorage = mediaStorage;
        this.interactionService = interactionService;
        this.transcodeService = transcodeService;
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

    @Override
    public DiscoverResult discover(String keyword, Long categoryId, Long tagId, int limit, int page, String sort, String contentType, Long viewerId) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String keywordPattern = normalizedKeyword == null ? null : normalizedKeyword.replace("=", "==").replace("%", "=%").replace("_", "=_");
        List<Video> rows = videoMapper.selectDiscoverVideos(normalizedKeyword, keywordPattern, categoryId, tagId,
                sort, contentType, limit + 1, (long) (page - 1) * limit);
        boolean hasMore = rows.size() > limit;
        List<Video> filtered = rows.stream().limit(limit).toList();
        boolean reuseFilteredForRanking = normalizedKeyword == null && categoryId == null && tagId == null
                && "ALL".equals(contentType) && page == 1 && !"LATEST".equals(sort) && limit >= 5;
        List<Video> hotRows = reuseFilteredForRanking ? filtered.stream().limit(5).toList()
                : videoMapper.selectDiscoverVideos(null, null, null, null, "HOT", "ALL", 5, 0);
        List<Video> rankingRows = new java.util.ArrayList<>(hotRows.stream().limit(4).toList());
        Video fresh = videoMapper.selectFreshDiscoverVideo(rankingRows.stream().map(Video::getId).toList());
        if (fresh != null) rankingRows.add(fresh);
        else if (hotRows.size() > 4) rankingRows.add(hotRows.get(4));
        DiscoverQueryRows.Summary summaryRow = videoMapper.selectDiscoverSummary();
        DiscoverQueryRows.DurationStats durationRow = videoMapper.selectDiscoverDurationStats();
        Summary summary = summaryRow == null ? new Summary(0, 0, 0, 0, 0)
                : new Summary(summaryRow.getTotalVideos(), summaryRow.getTotalWatchTimeMs(),
                summaryRow.getEffectivePlayCount(), summaryRow.getAverageWatchTimeMs(), summaryRow.getTodayNewCount());
        List<CategoryStat> categoryStats = videoMapper.selectDiscoverCategoryStats().stream()
                .map(row -> new CategoryStat(row.getId(), row.getName(), row.getVideoCount())).toList();
        List<DurationStat> durationStats = durationRow == null ? List.of() : List.of(
                new DurationStat("UNDER_10_SECONDS", "10秒以内", durationRow.getUnderTenSeconds()),
                new DurationStat("FROM_10_TO_30_SECONDS", "10–30秒", durationRow.getTenToThirtySeconds()),
                new DurationStat("FROM_30_TO_60_SECONDS", "30–60秒", durationRow.getThirtyToSixtySeconds()),
                new DurationStat("FROM_1_TO_3_MINUTES", "1–3分钟", durationRow.getOneToThreeMinutes()),
                new DurationStat("OVER_3_MINUTES", "3分钟以上", durationRow.getOverThreeMinutes()));
        List<Tag> tagOptions = videoMapper.selectDiscoverTags().stream()
                .map(row -> new Tag(row.getId(), row.getName())).toList();
        List<FeedItem> videos = assembleItems(filtered, viewerId);
        List<FeedItem> ranking = assembleItems(rankingRows, viewerId);
        return new DiscoverResult(summary, categoryStats, durationStats, tagOptions, ranking, videos, page, hasMore);
    }

    private CursorPage<FeedItem> assemblePage(List<Video> rows, int limit, Long viewerId) {
        boolean hasNext = rows.size() > limit;
        List<Video> videos = hasNext ? rows.subList(0, limit) : rows;
        List<FeedItem> items = assembleItems(videos, viewerId);
        String nextCursor = hasNext ? encodeCursor(videos.get(videos.size() - 1)) : null;
        return new CursorPage<>(items, nextCursor);
    }

    private List<FeedItem> assembleItems(List<Video> videos, Long viewerId) {
        if (videos.isEmpty()) return List.of();
        Map<Long, VideoAuthor> authors = authors(videos.stream().map(Video::getAuthorId).toList());
        Map<Long, MediaResource> originals = originals(videos);
        Map<Long, MediaResource> playbacks = transcodeService.readyMediaByVideoIds(
                videos.stream().map(Video::getId).toList());
        Map<Long, MediaResource> covers = covers(videos);
        List<Long> videoIds = videos.stream().map(Video::getId).toList();
        Map<Long, VideoStats> stats = stats(videoIds);
        Map<Long, Long> likeCounts = likeCounts(videoIds);
        Map<Long, Long> watchTimes = watchTimes(videoIds);
        Map<Long, List<Category>> categories = categories(videos.stream().map(Video::getId).toList());
        Map<Long, List<Tag>> tags = tags(videos.stream().map(Video::getId).toList());
        Set<Long> likedIds = viewerId == null ? Set.of() : interactionService.likedVideoIds(viewerId, videoIds);
        Set<Long> favoritedIds = viewerId == null ? Set.of() : interactionService.favoritedVideoIds(viewerId, videoIds);
        Set<Long> followingIds = viewerId == null ? Set.of()
                : interactionService.followingIds(viewerId, videos.stream().map(Video::getAuthorId).toList());
        return videos.stream().map(video -> feedItem(video,
                        video.getAuthorId() != null ? authors.getOrDefault(video.getAuthorId(), null) : null,
                        video.getOriginalMediaId() != null ? originals.getOrDefault(video.getOriginalMediaId(), null) : null,
                        playbacks.get(video.getId()),
                        video.getCoverMediaId() != null ? covers.getOrDefault(video.getCoverMediaId(), null) : null,
                        stats.getOrDefault(video.getId(), null), likeCounts.getOrDefault(video.getId(), 0L),
                        watchTimes.getOrDefault(video.getId(), 0L),
                        categories.getOrDefault(video.getId(), List.of()),
                        tags.getOrDefault(video.getId(), List.of()),
                        new ViewerState(likedIds.contains(video.getId()), favoritedIds.contains(video.getId())),
                        followingIds.contains(video.getAuthorId())))
                .toList();
    }

    @Override
    public VideoDetail getPublicVideo(long videoId, Long viewerId) {
        return publicDetail(videoId, viewerId, false);
    }

    @Override
    public List<PlaybackSource> playbackSources(long videoId) {
        Video video = videoMapper.selectPublicById(videoId);
        if (video == null) throw notFound();
        VideoAuthor author = authorMapper.selectById(video.getAuthorId());
        if (author == null || !"NORMAL".equals(author.getStatus())) throw notFound();
        if (video.getOriginalMediaId() == null) return List.of();
        MediaResource original = mediaMapper.selectById(video.getOriginalMediaId());
        if (original == null || !"VIDEO_ORIGINAL".equals(original.getResourceType())
                || !"READY".equals(original.getStorageState())) throw notFound();
        MediaResource playback = transcodeService.readyMediaByVideoIds(List.of(videoId)).get(videoId);
        List<PlaybackSource> sources = new java.util.ArrayList<>();
        String originalUrl = url(original);
        String playbackUrl = url(playback);
        if (playbackUrl != null && !playbackUrl.equals(originalUrl)) {
            sources.add(new PlaybackSource("optimized", "流畅版", playbackUrl, playback.getWidth(), playback.getHeight()));
        }
        if (originalUrl != null) {
            sources.add(new PlaybackSource("original", "原画", originalUrl, original.getWidth(), original.getHeight()));
        }
        return List.copyOf(sources);
    }

    @Override
    @Transactional
    public VideoDetail openPublicContent(long videoId, Long viewerId) {
        return publicDetail(videoId, viewerId, true);
    }

    private VideoDetail publicDetail(long videoId, Long viewerId, boolean countArticleView) {
        Video video = videoMapper.selectPublicById(videoId);
        if (video == null) throw notFound();
        VideoAuthor author = authorMapper.selectById(video.getAuthorId());
        if (author == null || !"NORMAL".equals(author.getStatus())) throw notFound();
        MediaResource original = video.getOriginalMediaId() == null ? null : mediaMapper.selectById(video.getOriginalMediaId());
        if (video.getOriginalMediaId() != null && (original == null || !"VIDEO_ORIGINAL".equals(original.getResourceType()) || !"READY".equals(original.getStorageState()))) {
            throw notFound();
        }
        MediaResource cover = cover(video.getCoverMediaId());
        MediaResource playback = transcodeService.readyMediaByVideoIds(List.of(videoId)).get(videoId);
        if (countArticleView && video.getOriginalMediaId() == null) {
            statsMapper.incrementPlay(videoId);
            statsMapper.incrementDailyArticlePlay(videoId, java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        }
        VideoStats stats = statsMapper.selectById(video.getId());
        long likeCount = likeCounts(List.of(videoId)).getOrDefault(videoId, 0L);
        long watchTimeMs = watchTimes(List.of(videoId)).getOrDefault(videoId, 0L);
        boolean liked = viewerId != null && interactionService.likedVideoIds(viewerId, List.of(video.getId())).contains(video.getId());
        boolean favorited = viewerId != null && interactionService.favoritedVideoIds(viewerId, List.of(video.getId())).contains(video.getId());
        boolean following = viewerId != null && interactionService.isFollowing(viewerId, video.getAuthorId());
        return new VideoDetail(video.getId(), author.getId(), author.getNickname(), video.getTitle(), video.getDescription(),
                original == null ? null : original.getDurationMs(),
                url(playback != null ? playback : original),
                contentCover(video, cover), categories(List.of(video.getId())).getOrDefault(video.getId(), List.of()),
                tags(List.of(video.getId())).getOrDefault(video.getId(), List.of()), stats(stats, likeCount, watchTimeMs),
                video.getPublishedAt(), new ViewerState(liked, favorited), following);
    }

    private FeedItem feedItem(Video video, VideoAuthor author, MediaResource original, MediaResource playback,
                              MediaResource cover,
                              VideoStats stats, long likeCount, long watchTimeMs, List<Category> categories, List<Tag> tags,
                              ViewerState state, boolean authorFollowing) {
        String videoUrl = url(playback != null ? playback : original);
        return new FeedItem(video.getId(), author.getId(), author.getNickname(), video.getTitle(), video.getDescription(),
                original == null ? null : original.getDurationMs(), videoUrl, contentCover(video, cover), categories, tags,
                stats(stats, likeCount, watchTimeMs),
                video.getPublishedAt(), state, authorFollowing);
    }

    private Map<Long, VideoAuthor> authors(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return authorMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(VideoAuthor::getId, Function.identity()));
    }

    private Map<Long, MediaResource> originals(List<Video> videos) {
        List<Long> ids = videos.stream().map(Video::getOriginalMediaId).filter(java.util.Objects::nonNull).toList();
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

    private Map<Long, Long> likeCounts(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return statsMapper.selectLikeCounts(ids).stream().collect(Collectors.toMap(
                DiscoverQueryRows.LikeCount::getVideoId, DiscoverQueryRows.LikeCount::getLikeCount));
    }

    private Map<Long, Long> watchTimes(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<DiscoverQueryRows.WatchTime> rows = videoMapper.selectWatchTimes(ids);
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream().collect(Collectors.toMap(DiscoverQueryRows.WatchTime::getVideoId,
                DiscoverQueryRows.WatchTime::getWatchTimeMs, Long::sum));
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

    private Map<Long, List<Category>> categories(Collection<Long> videoIds) {
        if (videoIds.isEmpty()) return Map.of();
        Map<Long, List<Long>> categoryIds = categoryMapMapper.selectList(new LambdaQueryWrapper<VideoCategoryMap>()
                        .in(VideoCategoryMap::getVideoId, videoIds))
                .stream().collect(Collectors.groupingBy(VideoCategoryMap::getVideoId,
                        LinkedHashMap::new, Collectors.mapping(VideoCategoryMap::getCategoryId, Collectors.toList())));
        List<Long> allCategoryIds = categoryIds.values().stream().flatMap(List::stream).distinct().toList();
        Map<Long, ContentCategory> values = allCategoryIds.isEmpty() ? Map.of() : categoryMapper.selectBatchIds(allCategoryIds).stream()
                .filter(category -> "ENABLED".equals(category.getStatus()))
                .collect(Collectors.toMap(ContentCategory::getId, Function.identity()));
        return categoryIds.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue().stream().map(values::get).filter(java.util.Objects::nonNull)
                        .map(category -> new Category(category.getId(), category.getName())).toList()));
    }

    private Cover cover(MediaResource media) {
        return media == null ? null : new Cover(media.getId(), media.getContentType(), media.getWidth(), media.getHeight(),
                url(media));
    }

    private Cover contentCover(Video video, MediaResource media) {
        if (video.getOriginalMediaId() != null && media != null) return cover(media);
        List<String> images = ContentBody.images(video.getDescription());
        return images.isEmpty() ? null : new Cover(video.getId(), "image/*", null, null, images.get(0));
    }

    private String url(MediaResource media) {
        if (media == null) return null;
        // Rebuild the public URL from the storage key when possible. Persisted
        // absolute URLs may contain a development-only host such as localhost.
        return media.getRelativePath() != null ? mediaStorage.getUrl(media.getRelativePath())
                : media.getUrl();
    }

    private Stats stats(VideoStats value, long likeCount, long watchTimeMs) {
        return value == null ? new Stats(0, likeCount, 0, 0, watchTimeMs) : new Stats(value.getPlayCount(), likeCount,
                value.getFavoriteCount(), value.getCommentCount(), watchTimeMs);
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
