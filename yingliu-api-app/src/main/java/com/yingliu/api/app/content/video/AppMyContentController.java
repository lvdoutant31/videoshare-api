package com.yingliu.api.app.content.video;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yingliu.api.app.security.AppActorProvider;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.domain.content.video.Video;
import com.yingliu.domain.content.video.MediaResource;
import com.yingliu.domain.content.video.VideoStats;
import com.yingliu.media.storage.MediaStorage;
import com.yingliu.domain.content.category.ContentCategory;
import com.yingliu.domain.content.tag.ContentTag;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.app.mapper.AppUserMapper;
import com.yingliu.service.content.video.VideoAdminService;
import com.yingliu.service.content.video.VideoTranscodeService;
import com.yingliu.service.content.video.mapper.VideoMapper;
import com.yingliu.service.content.video.mapper.VideoStatsMapper;
import com.yingliu.service.content.video.DiscoverQueryRows;
import com.yingliu.service.content.category.mapper.ContentCategoryMapper;
import com.yingliu.service.content.tag.mapper.ContentTagMapper;
import com.yingliu.service.content.video.mapper.VideoCategoryMapMapper;
import com.yingliu.service.content.video.mapper.VideoTagMapMapper;
import com.yingliu.domain.content.video.VideoCategoryMap;
import com.yingliu.domain.content.video.VideoTagMap;
import com.yingliu.service.content.video.ContentBody;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 用户自己的投稿管理入口。所有读写均由当前登录用户 ID 限定。 */
@RestController
@RequestMapping("/api/v1/app/me/content")
public class AppMyContentController {
    private final AppActorProvider actor;
    private final AppUserMapper users;
    private final VideoMapper videos;
    private final VideoAdminService videoService;
    private final ContentCategoryMapper categories;
    private final ContentTagMapper tags;
    private final VideoCategoryMapMapper categoryMaps;
    private final VideoTagMapMapper tagMaps;
    private final VideoTranscodeService transcodes;
    private final MediaStorage mediaStorage;
    private final VideoStatsMapper statsMapper;

    public AppMyContentController(AppActorProvider actor, AppUserMapper users, VideoMapper videos,
                                  VideoAdminService videoService, ContentCategoryMapper categories,
                                  ContentTagMapper tags, VideoCategoryMapMapper categoryMaps,
                                  VideoTagMapMapper tagMaps, VideoTranscodeService transcodes, MediaStorage mediaStorage,
                                  VideoStatsMapper statsMapper) {
        this.actor = actor;
        this.users = users;
        this.videos = videos;
        this.videoService = videoService;
        this.categories = categories;
        this.tags = tags;
        this.categoryMaps = categoryMaps;
        this.tagMaps = tagMaps;
        this.transcodes = transcodes;
        this.mediaStorage = mediaStorage;
        this.statsMapper = statsMapper;
    }

    @GetMapping
    public ApiResponse<List<AppMyContentDtos.Detail>> list(HttpServletRequest request) {
        long userId = requirePublisher();
        List<Video> rows = videos.selectList(new LambdaQueryWrapper<Video>()
                        .eq(Video::getAuthorId, userId).eq(Video::getDeleted, 0)
                        .orderByDesc(Video::getUpdatedAt).orderByDesc(Video::getId).last("LIMIT 100"));
        return ApiResponse.ok(details(rows), trace(request));
    }

    @GetMapping("/page")
    public ApiResponse<ContentPage> page(@RequestParam(defaultValue = "PUBLISHED") String status,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        long userId = requirePublisher();
        if (limit < 1 || limit > 50) throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "每页数量需为 1 至 50");
        var countQuery = ownContentQuery(userId, status);
        long total = videos.selectCount(countQuery);
        var query = ownContentQuery(userId, status);
        if (cursor != null && !cursor.isBlank()) {
            try {
                long id = Long.parseLong(cursor);
                if (id <= 0) throw new NumberFormatException();
                query.lt(Video::getId, id);
            } catch (NumberFormatException e) {
                throw new BusinessException("INVALID_CURSOR", HttpStatus.UNPROCESSABLE_ENTITY, "分页游标不合法");
            }
        }
        var rows = videos.selectList(query.orderByDesc(Video::getId).last("LIMIT " + (limit + 1)));
        boolean hasNext = rows.size() > limit;
        var slice = hasNext ? rows.subList(0, limit) : rows;
        var items = details(slice);
        return ApiResponse.ok(new ContentPage(items, hasNext ? String.valueOf(slice.get(slice.size() - 1).getId()) : null, total), trace(request));
    }

    private LambdaQueryWrapper<Video> ownContentQuery(long userId, String status) {
        var query = new LambdaQueryWrapper<Video>().eq(Video::getAuthorId, userId).eq(Video::getDeleted, 0);
        switch (status) {
            case "ALL" -> { }
            case "PUBLISHED" -> query.eq(Video::getPublishStatus, "PUBLISHED");
            case "REVIEW" -> query.in(Video::getAuditStatus, "PENDING_REVIEW", "CLAIMED");
            case "REVISION" -> query.in(Video::getAuditStatus, "REJECTED", "REVISION_REQUIRED");
            case "OFF_SHELF" -> query.eq(Video::getPublishStatus, "OFF_SHELF");
            case "UNPUBLISHED" -> query.in(Video::getAuditStatus, "DRAFT", "APPROVED").eq(Video::getPublishStatus, "UNPUBLISHED");
            default -> throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "作品状态不合法");
        }
        return query;
    }

    public record ContentPage(List<AppMyContentDtos.Detail> items, String nextCursor, long total) { }

    @GetMapping("/{id}")
    public ApiResponse<AppMyContentDtos.Detail> get(@PathVariable long id, HttpServletRequest request) {
        long userId = requirePublisher();
        Video owned = requireOwned(id, userId);
        return ApiResponse.ok(details(List.of(owned)).get(0), trace(request));
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<AppMyContentDtos.Detail> update(@PathVariable long id, @RequestBody UpdateContent input,
                                                              HttpServletRequest request) {
        long userId = requirePublisher();
        Video current = requireOwned(id, userId);
        String title = input.title() == null ? "" : input.title().trim();
        if (title.isEmpty() || title.length() > 120) throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "标题需为 1 至 120 个字符");
        String description = ContentBody.normalize(input.description(), current.getOriginalMediaId());
        List<Long> categoryIds = input.categoryIds() == null ? List.of() : input.categoryIds().stream().distinct().toList();
        List<Long> tagIds = input.tagIds() == null ? List.of() : input.tagIds().stream().distinct().toList();
        if (categoryIds.size() < 1 || categoryIds.size() > 3 || tagIds.size() > 10
                || categoryIds.stream().anyMatch(x -> x == null || x <= 0) || tagIds.stream().anyMatch(x -> x == null || x <= 0)) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "分类或标签数量不合法");
        }
        if (categories.selectCount(new QueryWrapper<ContentCategory>().in("id", categoryIds).eq("status", "ENABLED")) != categoryIds.size()
                || (!tagIds.isEmpty() && tags.selectCount(new QueryWrapper<ContentTag>().in("id", tagIds).eq("status", "ENABLED").isNull("merged_to_id")) != tagIds.size())) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "分类或标签已不可用");
        }
        int changed = videos.update(null, new LambdaUpdateWrapper<Video>().eq(Video::getId, id)
                .eq(Video::getAuthorId, userId).eq(Video::getDeleted, 0)
                .eq(Video::getCurrentVersion, input.currentVersion())
                .in(Video::getAuditStatus, "DRAFT", "PENDING_REVIEW", "REJECTED", "REVISION_REQUIRED", "APPROVED")
                .in(Video::getPublishStatus, "UNPUBLISHED", "PUBLISHED", "OFF_SHELF")
                .set(Video::getTitle, title).set(Video::getDescription, description)
                .set(Video::getAuditStatus, "PENDING_REVIEW").set(Video::getPublishStatus, "UNPUBLISHED")
                .set(Video::getPublishedAt, null).set(Video::getScheduledAt, null).set(Video::getOffShelfAt, null)
                .set(Video::getOffShelfReason, null).set(Video::getReviewClaimedBy, null).set(Video::getReviewClaimedAt, null)
                .setSql("current_version = current_version + 1")
                .set(Video::getUpdatedBy, userId));
        if (changed != 1) {
            throw new BusinessException("STATE_CONFLICT", HttpStatus.CONFLICT, "内容状态已变化，请刷新后重试");
        }
        categoryMaps.delete(new LambdaQueryWrapper<VideoCategoryMap>().eq(VideoCategoryMap::getVideoId, id));
        tagMaps.delete(new LambdaQueryWrapper<VideoTagMap>().eq(VideoTagMap::getVideoId, id));
        categoryIds.forEach(categoryId -> categoryMaps.insert(new VideoCategoryMap(id, categoryId)));
        tagIds.forEach(tagId -> tagMaps.insert(new VideoTagMap(id, tagId)));
        return ApiResponse.ok(details(List.of(current)).get(0), trace(request));
    }

    @PostMapping("/{id}/off-shelf")
    public ApiResponse<Void> offShelf(@PathVariable long id, HttpServletRequest request) {
        long userId = requirePublisher();
        requireOwned(id, userId);
        int changed = videos.update(null, new LambdaUpdateWrapper<Video>()
                .eq(Video::getId, id).eq(Video::getAuthorId, userId).eq(Video::getDeleted, 0)
                .eq(Video::getAuditStatus, "APPROVED").eq(Video::getPublishStatus, "PUBLISHED")
                .set(Video::getPublishStatus, "OFF_SHELF").setSql("off_shelf_at = NOW(3)")
                .set(Video::getOffShelfReason, "作者主动下架").set(Video::getUpdatedBy, userId)
                .setSql("updated_at = NOW(3)"));
        if (changed != 1) throw new BusinessException("STATE_CONFLICT", HttpStatus.CONFLICT, "内容状态已变化，请刷新后重试");
        return ApiResponse.ok(null, trace(request));
    }

    private long requirePublisher() {
        long id = actor.currentUserId();
        if (users.countNormal(id) != 1) throw new BusinessException("PERMISSION_DENIED", HttpStatus.FORBIDDEN, "当前账号不可管理内容");
        return id;
    }

    private List<AppMyContentDtos.Detail> details(List<Video> rows) {
        if (rows.isEmpty()) return List.of();
        List<Long> ids = rows.stream().map(Video::getId).distinct().toList();
        Map<Long, MediaResource> playbacks = transcodes.readyMediaByVideoIds(ids);
        Map<Long, VideoStats> stats = statsMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(VideoStats::getVideoId, value -> value));
        Map<Long, Long> likes = statsMapper.selectLikeCounts(ids).stream()
                .collect(Collectors.toMap(DiscoverQueryRows.LikeCount::getVideoId, DiscoverQueryRows.LikeCount::getLikeCount));
        return rows.stream().map(video -> {
            VideoStats counts = stats.get(video.getId());
            long plays = counts == null || counts.getPlayCount() == null ? 0 : counts.getPlayCount();
            return detail(videoService.get(video.getId()), playbacks.get(video.getId()),
                    likes.getOrDefault(video.getId(), 0L), plays);
        }).toList();
    }

    private AppMyContentDtos.Detail detail(VideoAdminService.VideoDetail value, MediaResource playback, long likes, long plays) {
        String videoUrl = value.originalMedia() == null ? null : value.originalMedia().url();
        if (value.originalMedia() != null && playback != null) {
            videoUrl = playback.getRelativePath() != null ? mediaStorage.getUrl(playback.getRelativePath()) : playback.getUrl();
        }
        return AppMyContentDtos.detail(value, videoUrl, likes, plays);
    }

    private Video requireOwned(long id, long userId) {
        Video video = videos.selectOne(new LambdaQueryWrapper<Video>().eq(Video::getId, id)
                .eq(Video::getAuthorId, userId).eq(Video::getDeleted, 0));
        if (video == null) throw notManageable();
        return video;
    }

    private BusinessException notManageable() {
        return new BusinessException("CONTENT_NOT_FOUND", HttpStatus.NOT_FOUND, "内容不存在或当前状态不可操作");
    }

    private String trace(HttpServletRequest request) {
        return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
    }

    public record UpdateContent(String title, String description, List<Long> categoryIds, List<Long> tagIds, int currentVersion) { }
}
