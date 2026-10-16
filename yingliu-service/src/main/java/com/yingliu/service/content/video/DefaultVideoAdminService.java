package com.yingliu.service.content.video;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.domain.content.category.ContentCategory;
import com.yingliu.domain.content.tag.ContentTag;
import com.yingliu.domain.content.video.AdminUser;
import com.yingliu.domain.content.video.MediaResource;
import com.yingliu.domain.content.video.Video;
import com.yingliu.domain.content.video.VideoAuditRecord;
import com.yingliu.domain.content.video.VideoAuthor;
import com.yingliu.domain.content.video.VideoCategoryMap;
import com.yingliu.domain.content.video.VideoTagMap;
import com.yingliu.domain.content.video.VideoPublishSchedule;
import com.yingliu.media.storage.MediaStorage;
import com.yingliu.service.content.category.mapper.ContentCategoryMapper;
import com.yingliu.service.content.shared.ServicePage;
import com.yingliu.service.content.tag.mapper.ContentTagMapper;
import com.yingliu.service.content.video.mapper.AdminUserMapper;
import com.yingliu.service.content.video.mapper.MediaResourceMapper;
import com.yingliu.service.content.video.mapper.VideoAuthorMapper;
import com.yingliu.service.content.video.mapper.VideoAuditRecordMapper;
import com.yingliu.service.content.video.mapper.VideoCategoryMapMapper;
import com.yingliu.service.content.video.mapper.VideoMapper;
import com.yingliu.service.content.video.mapper.VideoPublishScheduleMapper;
import com.yingliu.service.content.video.mapper.VideoTagMapMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 视频管理事务实现，协调媒体、分类标签、审核记录和发布计划的状态变化。 */
@Service
@Profile("!test")
public class DefaultVideoAdminService implements VideoAdminService {
    private final VideoMapper videoMapper;
    private final MediaResourceMapper mediaMapper;
    private final VideoAuthorMapper authorMapper;
    private final AdminUserMapper adminUserMapper;
    private final ContentCategoryMapper categoryMapper;
    private final ContentTagMapper tagMapper;
    private final VideoCategoryMapMapper categoryMapMapper;
    private final VideoTagMapMapper tagMapMapper;
    private final VideoAuditRecordMapper auditRecordMapper;
    private final VideoPublishScheduleMapper publishScheduleMapper;
    private final MediaStorage mediaStorage;

    public DefaultVideoAdminService(VideoMapper videoMapper, MediaResourceMapper mediaMapper,
                                    VideoAuthorMapper authorMapper, AdminUserMapper adminUserMapper,
                                    ContentCategoryMapper categoryMapper, ContentTagMapper tagMapper,
                                    VideoCategoryMapMapper categoryMapMapper, VideoTagMapMapper tagMapMapper,
                                    VideoAuditRecordMapper auditRecordMapper, VideoPublishScheduleMapper publishScheduleMapper,
                                    MediaStorage mediaStorage) {
        this.videoMapper = videoMapper;
        this.mediaMapper = mediaMapper;
        this.authorMapper = authorMapper;
        this.adminUserMapper = adminUserMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.categoryMapMapper = categoryMapMapper;
        this.tagMapMapper = tagMapMapper;
        this.auditRecordMapper = auditRecordMapper;
        this.publishScheduleMapper = publishScheduleMapper;
        this.mediaStorage = mediaStorage;
    }

    @Override
    public ServicePage<VideoSummary> list(VideoQuery query) {
        Page<Video> page = videoMapper.selectPage(new Page<>(query.page(), query.pageSize()), new LambdaQueryWrapper<Video>()
                .like(query.keyword() != null && !query.keyword().isBlank(), Video::getTitle, query.keyword())
                .eq(query.authorId() != null, Video::getAuthorId, query.authorId())
                .eq(query.auditStatus() != null && !query.auditStatus().isBlank(), Video::getAuditStatus, query.auditStatus())
                .eq(query.publishStatus() != null && !query.publishStatus().isBlank(), Video::getPublishStatus, query.publishStatus())
                .orderByDesc(Video::getUpdatedAt)
                .orderByDesc(Video::getId));
        Map<Long, VideoAuthor> authors = authorsById(page.getRecords().stream().map(Video::getAuthorId).toList());
        Map<Long, Long> watchTimes = watchTimes(page.getRecords().stream().map(Video::getId).toList());
        return new ServicePage<>(page.getRecords().stream()
                .map(video -> summary(video, authors.get(video.getAuthorId()), watchTimes.getOrDefault(video.getId(), 0L)))
                .toList(), page.getTotal());
    }

    @Override
    public VideoDetail get(long videoId) {
        return detail(requireVideo(videoId));
    }

    @Override
    @Transactional
    public VideoDetail update(long videoId, long actorAdminId, UpdateCommand command) {
        requireNormalAdmin(actorAdminId);
        Video video = requireVideo(videoId);
        String title = requireText(command.title(), 120, "视频标题不能为空");
        Long mediaId = command.originalMediaId() == null ? video.getOriginalMediaId() : command.originalMediaId();
        String description = ContentBody.normalize(command.description(), mediaId);
        String visibility = normalizeVisibility(command.visibility());
        List<Long> categoryIds = validateIds(command.categoryIds(), 1, 3, "分类");
        List<Long> tagIds = validateIds(command.tagIds(), 0, 10, "标签");
        requireEligibleCategories(categoryIds); requireEligibleTags(tagIds);
        Long originalMediaId = command.originalMediaId();
        if (originalMediaId != null) requireMedia(originalMediaId, "VIDEO_ORIGINAL");
        if (videoMapper.updateMetadata(videoId, actorAdminId, video.getCurrentVersion(), title, description,
                visibility, originalMediaId == null ? video.getOriginalMediaId() : originalMediaId) != 1) throw stateConflict();
        categoryMapMapper.delete(new LambdaQueryWrapper<VideoCategoryMap>().eq(VideoCategoryMap::getVideoId, videoId));
        tagMapMapper.delete(new LambdaQueryWrapper<VideoTagMap>().eq(VideoTagMap::getVideoId, videoId));
        categoryIds.forEach(id -> categoryMapMapper.insert(new VideoCategoryMap(videoId, id)));
        tagIds.forEach(id -> tagMapMapper.insert(new VideoTagMap(videoId, id)));
        return get(videoId);
    }

    @Override
    @Transactional
    public VideoDetail claimReview(long videoId, long actorAdminId, int currentVersion) {
        requireNormalAdmin(actorAdminId); Video video = requireVideo(videoId);
        if (videoMapper.claimReview(videoId, actorAdminId, currentVersion) != 1) throw stateConflict();
        insertAuditRecord(video, actorAdminId, "CLAIMED", null); return get(videoId);
    }

    @Override
    @Transactional
    public VideoDetail releaseReview(long videoId, long actorAdminId, int currentVersion) {
        requireNormalAdmin(actorAdminId); Video video = requireVideo(videoId);
        if (videoMapper.releaseReview(videoId, actorAdminId, currentVersion) != 1) throw stateConflict();
        insertAuditRecord(video, actorAdminId, "RELEASED", null); return get(videoId);
    }

    @Override
    @Transactional
    public VideoDetail requestRevision(long videoId, long actorAdminId, int currentVersion, String reason) {
        requireNormalAdmin(actorAdminId); Video video = requireVideo(videoId);
        String normalized = requireText(reason, 1000, "修改原因不能为空");
        if (videoMapper.requestRevision(videoId, actorAdminId, currentVersion) != 1) throw stateConflict();
        insertAuditRecord(video, actorAdminId, "REVISION_REQUIRED", normalized); return get(videoId);
    }

    @Override
    @Transactional
    public VideoDetail createDraft(CreateDraftCommand command) {
        long authorId = currentAuthorId(command.actorAdminId());
        return createContent(command, authorId, "DRAFT");
    }

    @Override
    @Transactional
    public VideoDetail createUserSubmission(long userId, Long mediaId, String title, String description,
                                           List<Long> categories, List<Long> tags) {
        requireEligibleAuthor(userId);
        VideoDetail result = createContent(new CreateDraftCommand(userId, userId, mediaId, null,
                title, description, null, categories, tags, "PUBLIC"), userId, "PENDING_REVIEW");
        insertAuditRecord(requireVideo(result.id()), null, "SUBMITTED", null);
        return result;
    }

    private VideoDetail createContent(CreateDraftCommand command, long authorId, String auditStatus) {
        String body = ContentBody.normalize(command.description(), command.originalMediaId());
        if (command.originalMediaId() != null) requireMedia(command.originalMediaId(), "VIDEO_ORIGINAL");
        if (command.coverMediaId() != null) requireMedia(command.coverMediaId(), "COVER");
        List<Long> categoryIds = validateIds(command.categoryIds(), 1, 3, "分类");
        List<Long> tagIds = validateIds(command.tagIds(), 0, 10, "标签");
        requireEligibleCategories(categoryIds);
        requireEligibleTags(tagIds);

        Video video = new Video();
        video.setAuthorId(authorId);
        video.setOriginalMediaId(command.originalMediaId());
        video.setCoverMediaId(command.coverMediaId());
        video.setTitle(requireText(command.title(), 120, "视频标题不能为空"));
        video.setDescription(body);
        video.setSourceNote(normalizeOptional(command.sourceNote(), 128, "来源说明最多 128 个字符"));
        video.setVisibility(normalizeVisibility(command.visibility()));
        video.setAuditStatus(auditStatus);
        video.setPublishStatus("UNPUBLISHED");
        video.setRiskLevel("NONE");
        video.setManualWeight(0);
        video.setCurrentVersion(1);
        video.setCreatedBy(command.actorAdminId());
        video.setUpdatedBy(command.actorAdminId());
        videoMapper.insert(video);
        categoryIds.forEach(categoryId -> categoryMapMapper.insert(new VideoCategoryMap(video.getId(), categoryId)));
        tagIds.forEach(tagId -> tagMapMapper.insert(new VideoTagMap(video.getId(), tagId)));
        return detail(video);
    }

    @Override
    @Transactional
    public VideoDetail submitForReview(long videoId, long actorAdminId) {
        requireNormalAdmin(actorAdminId);
        Video video = requireVideo(videoId);
        if (videoMapper.submitForReview(videoId, actorAdminId) != 1) throw stateConflict();
        insertAuditRecord(video, null, "SUBMITTED", null);
        video.setAuditStatus("PENDING_REVIEW");
        video.setUpdatedBy(actorAdminId);
        return detail(video);
    }

    @Override
    public ServicePage<ReviewQueueItem> listReviewQueue(ReviewQueueQuery query) {
        Page<Video> page = videoMapper.selectReviewPage(new Page<>(query.page(), query.pageSize()), query.keyword(),
                query.authorId(), query.riskLevel(), query.actorAdminId());
        Map<Long, VideoAuthor> authors = authorsById(page.getRecords().stream().map(Video::getAuthorId).toList());
        return new ServicePage<>(page.getRecords().stream().map(video -> {
            VideoAuthor author = authors.get(video.getAuthorId());
            return new ReviewQueueItem(video.getId(), video.getAuthorId(), author == null ? "" : author.getNickname(),
                    video.getTitle(), video.getRiskLevel(), video.getCurrentVersion(), video.getUpdatedAt());
        }).toList(), page.getTotal());
    }

    @Override
    @Transactional
    public VideoDetail approveReview(long videoId, long actorAdminId, int currentVersion, String reason) {
        requireNormalAdmin(actorAdminId);
        Video video = requireVideo(videoId);
        if (currentVersion <= 0 || videoMapper.approveReview(videoId, actorAdminId, currentVersion) != 1) throw stateConflict();
        insertAuditRecord(video, actorAdminId, "APPROVED", normalizeOptional(reason, 1000, "审核说明最多 1000 个字符"));
        video.setAuditStatus("APPROVED");
        video.setUpdatedBy(actorAdminId);
        return detail(video);
    }

    @Override
    @Transactional
    public VideoDetail rejectReview(long videoId, long actorAdminId, int currentVersion, String reason) {
        requireNormalAdmin(actorAdminId);
        Video video = requireVideo(videoId);
        if (currentVersion <= 0 || videoMapper.rejectReview(videoId, actorAdminId, currentVersion) != 1) throw stateConflict();
        insertAuditRecord(video, actorAdminId, "REJECTED", requireText(reason, 1000, "驳回原因不能为空"));
        video.setAuditStatus("REJECTED");
        video.setUpdatedBy(actorAdminId);
        return detail(video);
    }

    @Override
    @Transactional
    public VideoDetail publishNow(long videoId, long actorAdminId) {
        requireNormalAdmin(actorAdminId);
        requireVideo(videoId);
        if (videoMapper.publishNow(videoId, actorAdminId) != 1) throw stateConflict();
        return get(videoId);
    }

    @Override
    @Transactional
    public VideoDetail schedulePublish(long videoId, long actorAdminId, java.time.LocalDateTime scheduledAt) {
        requireNormalAdmin(actorAdminId);
        requireVideo(videoId);
        if (scheduledAt == null || !scheduledAt.isAfter(java.time.LocalDateTime.now())) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "发布时间必须晚于当前时间");
        }
        if (videoMapper.schedulePublish(videoId, actorAdminId, scheduledAt) != 1) throw stateConflict();
        VideoPublishSchedule schedule = new VideoPublishSchedule();
        schedule.setVideoId(videoId);
        schedule.setScheduledAt(scheduledAt);
        schedule.setStatus("PENDING");
        schedule.setCreatedBy(actorAdminId);
        publishScheduleMapper.insert(schedule);
        return get(videoId);
    }

    @Override
    @Transactional
    public VideoDetail cancelScheduledPublish(long videoId, long actorAdminId) {
        requireNormalAdmin(actorAdminId);
        requireVideo(videoId);
        if (videoMapper.cancelScheduledPublish(videoId, actorAdminId) != 1) throw stateConflict();
        publishScheduleMapper.cancelActiveByVideoId(videoId);
        return get(videoId);
    }

    @Override
    @Transactional
    public VideoDetail offShelf(long videoId, long actorAdminId, String reason) {
        requireNormalAdmin(actorAdminId);
        requireVideo(videoId);
        if (videoMapper.offShelf(videoId, actorAdminId, requireText(reason, 512, "下架原因不能为空")) != 1) throw stateConflict();
        publishScheduleMapper.cancelActiveByVideoId(videoId);
        return get(videoId);
    }

    @Override
    @Transactional
    public VideoDetail restore(long videoId, long actorAdminId) {
        requireNormalAdmin(actorAdminId);
        Video video = requireVideo(videoId);
        if (videoMapper.restore(videoId, actorAdminId) != 1) throw stateConflict();
        insertAuditRecord(video, null, "SUBMITTED", null);
        return get(videoId);
    }

    @Override
    @Transactional
    public BatchOperationResult batchOffShelf(List<Long> videoIds, long actorAdminId, String reason) {
        requireNormalAdmin(actorAdminId);
        String normalizedReason = requireText(reason, 512, "下架原因不能为空");
        return batch(videoIds, id -> {
            Video video = requireVideo(id);
            if ("OFF_SHELF".equals(video.getPublishStatus())) return true;
            if (videoMapper.offShelf(id, actorAdminId, normalizedReason) != 1) throw stateConflict();
            publishScheduleMapper.cancelActiveByVideoId(id);
            return true;
        });
    }

    @Override
    @Transactional
    public BatchOperationResult batchRestore(List<Long> videoIds, long actorAdminId) {
        requireNormalAdmin(actorAdminId);
        return batch(videoIds, id -> {
            Video video = requireVideo(id);
            if ("PENDING_REVIEW".equals(video.getAuditStatus()) && "UNPUBLISHED".equals(video.getPublishStatus())) return true;
            if (videoMapper.restore(id, actorAdminId) != 1) throw stateConflict();
            insertAuditRecord(video, null, "SUBMITTED", null);
            return true;
        });
    }

    @Override
    @Transactional
    public BatchOperationResult batchDelete(List<Long> videoIds, long actorAdminId, String reason) {
        requireNormalAdmin(actorAdminId);
        String normalizedReason = requireText(reason, 512, "删除原因不能为空");
        return batch(videoIds, id -> {
            requireVideo(id);
            if (videoMapper.deleteVideo(id, actorAdminId) != 1) throw stateConflict();
            videoMapper.deleteCommentsByVideoId(id, actorAdminId, normalizedReason);
            publishScheduleMapper.cancelActiveByVideoId(id);
            return true;
        });
    }

    @Override
    @Transactional
    public void retryTranscode(long videoId, String profile, long actorAdminId) {
        requireNormalAdmin(actorAdminId);
        requireVideo(videoId);
        String normalized = requireText(profile, 16, "转码规格不能为空");
        if (!normalized.matches("[A-Za-z0-9_-]+")) throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "转码规格不合法");
        if (videoMapper.retryTranscode(videoId, normalized) == 0 && videoMapper.retryTranscodeTask(videoId) == 0) throw stateConflict();
    }

    private BatchOperationResult batch(List<Long> ids, Function<Long, Boolean> operation) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id <= 0)
                || ids.stream().distinct().count() != ids.size()) throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "视频 ID 数量或格式不合法");
        List<Long> succeeded = new java.util.ArrayList<>();
        List<BatchFailure> failures = new java.util.ArrayList<>();
        for (Long id : ids) {
            try { if (operation.apply(id)) succeeded.add(id); }
            catch (BusinessException ex) { failures.add(new BatchFailure(id, ex.getCode(), ex.getMessage())); }
        }
        return new BatchOperationResult(List.copyOf(succeeded), List.copyOf(failures));
    }

    @Override
    @Transactional
    public void delete(long videoId, long actorAdminId, String reason) {
        requireNormalAdmin(actorAdminId);
        requireVideo(videoId);
        String normalizedReason = requireText(reason, 512, "删除原因不能为空");
        if (videoMapper.deleteVideo(videoId, actorAdminId) != 1) throw stateConflict();
        videoMapper.deleteCommentsByVideoId(videoId, actorAdminId, normalizedReason);
        publishScheduleMapper.cancelActiveByVideoId(videoId);
    }

    @Override
    public List<Long> claimDuePublishSchedules(java.time.LocalDateTime expiredBefore, int limit) {
        if (limit <= 0 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return publishScheduleMapper.selectDueOrExpiredIds(expiredBefore, limit).stream()
                .filter(scheduleId -> publishScheduleMapper.claimForProcessing(scheduleId, expiredBefore) == 1)
                .toList();
    }

    @Override
    @Transactional
    public void publishClaimedSchedule(long scheduleId) {
        VideoPublishSchedule schedule = publishScheduleMapper.selectById(scheduleId);
        if (schedule == null || !"PROCESSING".equals(schedule.getStatus())) return;
        if (videoMapper.publishScheduled(schedule.getVideoId()) == 1) {
            publishScheduleMapper.markExecuted(scheduleId);
        } else {
            publishScheduleMapper.markCancelled(scheduleId);
        }
    }

    @Override
    public List<AuditHistoryItem> auditHistory(long videoId) {
        requireVideo(videoId);
        return auditRecordMapper.selectList(new LambdaQueryWrapper<VideoAuditRecord>()
                        .eq(VideoAuditRecord::getVideoId, videoId)
                        .orderByDesc(VideoAuditRecord::getCreatedAt)
                        .orderByDesc(VideoAuditRecord::getId))
                .stream().map(item -> new AuditHistoryItem(item.getId(), item.getVideoVersion(), item.getReviewerAdminId(),
                        item.getAction(), item.getReason(), item.getClaimedAt(), item.getReviewedAt(), item.getCreatedAt()))
                .toList();
    }

    private void insertAuditRecord(Video video, Long reviewerAdminId, String action, String reason) {
        VideoAuditRecord record = new VideoAuditRecord();
        record.setVideoId(video.getId());
        record.setVideoVersion(video.getCurrentVersion());
        record.setReviewerAdminId(reviewerAdminId);
        record.setAction(action);
        record.setReason(reason);
        if ("CLAIMED".equals(action)) record.setClaimedAt(java.time.LocalDateTime.now());
        else if (!"SUBMITTED".equals(action)) record.setReviewedAt(java.time.LocalDateTime.now());
        auditRecordMapper.insert(record);
    }

    private VideoDetail detail(Video video) {
        VideoAuthor author = requireEligibleAuthor(video.getAuthorId());
        MediaResource original = video.getOriginalMediaId() == null ? null : requireMedia(video.getOriginalMediaId(), "VIDEO_ORIGINAL");
        MediaResource cover = video.getCoverMediaId() == null ? null : requireMedia(video.getCoverMediaId(), "COVER");
        Map<Long, ContentCategory> categories = categoriesById(mappedCategoryIds(video.getId()));
        Map<Long, ContentTag> tags = tagsById(mappedTagIds(video.getId()));
        long watchTimeMs = watchTimes(List.of(video.getId())).getOrDefault(video.getId(), 0L);
        return new VideoDetail(video.getId(), author.getId(), author.getNickname(), video.getTitle(), video.getDescription(),
                video.getSourceNote(), original == null ? null : mediaView(original), cover == null ? null : mediaView(cover), video.getAuditStatus(),
                video.getPublishStatus(), video.getVisibility(), video.getRiskLevel(), video.getCurrentVersion(),
                categories.values().stream().map(item -> new CategoryView(item.getId(), item.getName())).toList(),
                tags.values().stream().map(item -> new TagView(item.getId(), item.getName())).toList(),
                video.getScheduledAt(), video.getPublishedAt(), watchTimeMs, video.getCreatedAt(), video.getUpdatedAt());
    }

    private VideoSummary summary(Video video, VideoAuthor author, long watchTimeMs) {
        return new VideoSummary(video.getId(), video.getAuthorId(), author == null ? "" : author.getNickname(),
                video.getTitle(), video.getAuditStatus(), video.getPublishStatus(), video.getVisibility(),
                watchTimeMs, video.getCreatedAt(), video.getUpdatedAt());
    }

    private Map<Long, Long> watchTimes(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<DiscoverQueryRows.WatchTime> rows = videoMapper.selectWatchTimes(ids);
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream().collect(java.util.stream.Collectors.toMap(DiscoverQueryRows.WatchTime::getVideoId,
                DiscoverQueryRows.WatchTime::getWatchTimeMs, Long::sum));
    }

    private Video requireVideo(long id) {
        Video video = videoMapper.selectById(id);
        if (video == null) throw new BusinessException("VIDEO_NOT_FOUND", HttpStatus.NOT_FOUND, "视频不存在");
        return video;
    }

    @Override
    @Transactional
    public long currentAuthorId(long actorAdminId) {
        // Serialize first-time binding so concurrent uploads share one author profile.
        AdminUser admin = requireNormalAdmin(adminUserMapper.selectForAuthorBinding(actorAdminId));
        if (admin.getUserId() != null) return requireEligibleAuthor(admin.getUserId()).getId();
        long authorId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        if (authorMapper.insertAdminAuthor(authorId, admin.getUsername()) != 1
                || adminUserMapper.bindAuthor(actorAdminId, authorId) != 1) throw stateConflict();
        return authorId;
    }

    private AdminUser requireNormalAdmin(long id) {
        return requireNormalAdmin(adminUserMapper.selectById(id));
    }

    private AdminUser requireNormalAdmin(AdminUser admin) {
        if (admin == null) throw new BusinessException("ADMIN_NOT_FOUND", HttpStatus.NOT_FOUND, "后台账号不存在");
        if (!"NORMAL".equals(admin.getStatus())) {
            throw new BusinessException("ADMIN_NOT_ELIGIBLE", HttpStatus.UNPROCESSABLE_ENTITY, "后台账号不可用");
        }
        return admin;
    }

    private VideoAuthor requireEligibleAuthor(long id) {
        VideoAuthor author = authorMapper.selectById(id);
        if (author == null) throw new BusinessException("AUTHOR_NOT_FOUND", HttpStatus.NOT_FOUND, "作者不存在");
        if (!"NORMAL".equals(author.getStatus())) {
            throw new BusinessException("AUTHOR_NOT_ELIGIBLE", HttpStatus.UNPROCESSABLE_ENTITY, "作者不可用");
        }
        return author;
    }

    private MediaResource requireMedia(long id, String expectedType) {
        MediaResource media = mediaMapper.selectById(id);
        if (media == null) throw new BusinessException("MEDIA_NOT_FOUND", HttpStatus.NOT_FOUND, "媒体资源不存在");
        if (!expectedType.equals(media.getResourceType())) {
            throw new BusinessException("MEDIA_TYPE_INVALID", HttpStatus.UNPROCESSABLE_ENTITY, "媒体资源类型不匹配");
        }
        if (!"READY".equals(media.getStorageState())) {
            throw new BusinessException("MEDIA_NOT_READY", HttpStatus.UNPROCESSABLE_ENTITY, "媒体资源尚未就绪");
        }
        return media;
    }

    private void requireEligibleCategories(List<Long> ids) {
        Map<Long, ContentCategory> categories = categoriesById(ids);
        if (categories.size() != ids.size()) throw new BusinessException("CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND, "分类不存在");
        if (categories.values().stream().anyMatch(item -> !"ENABLED".equals(item.getStatus()))) {
            throw new BusinessException("CATEGORY_NOT_ELIGIBLE", HttpStatus.UNPROCESSABLE_ENTITY, "分类不可用");
        }
    }

    private void requireEligibleTags(List<Long> ids) {
        Map<Long, ContentTag> tags = tagsById(ids);
        if (tags.size() != ids.size()) throw new BusinessException("TAG_NOT_FOUND", HttpStatus.NOT_FOUND, "标签不存在");
        if (tags.values().stream().anyMatch(item -> !"ENABLED".equals(item.getStatus()) || item.getMergedToId() != null)) {
            throw new BusinessException("TAG_NOT_ELIGIBLE", HttpStatus.UNPROCESSABLE_ENTITY, "标签不可用");
        }
    }

    private Map<Long, VideoAuthor> authorsById(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return authorMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(VideoAuthor::getId, Function.identity()));
    }

    private Map<Long, ContentCategory> categoriesById(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return categoryMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(ContentCategory::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, ContentTag> tagsById(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return tagMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(ContentTag::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private List<Long> mappedCategoryIds(long videoId) {
        return categoryMapMapper.selectList(new LambdaQueryWrapper<VideoCategoryMap>().eq(VideoCategoryMap::getVideoId, videoId))
                .stream().map(VideoCategoryMap::getCategoryId).toList();
    }

    private List<Long> mappedTagIds(long videoId) {
        return tagMapMapper.selectList(new LambdaQueryWrapper<VideoTagMap>().eq(VideoTagMap::getVideoId, videoId))
                .stream().map(VideoTagMap::getTagId).toList();
    }

    private List<Long> validateIds(List<Long> ids, int minimum, int maximum, String label) {
        List<Long> normalized = ids == null ? List.of() : ids;
        if (normalized.size() < minimum || normalized.size() > maximum || normalized.stream().anyMatch(id -> id == null || id <= 0)
                || normalized.stream().distinct().count() != normalized.size()) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, label + "数量或 ID 不合法");
        }
        return normalized;
    }

    private String requireText(String value, int max, String blankMessage) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, normalized.isEmpty() ? blankMessage : "文本长度超出限制");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int max, String message) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, message);
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeVisibility(String value) {
        String normalized = value == null || value.isBlank() ? "PUBLIC" : value.trim();
        if (!"PUBLIC".equals(normalized) && !"PRIVATE".equals(normalized)) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "可见范围必须为 PUBLIC 或 PRIVATE");
        }
        return normalized;
    }

    private MediaView mediaView(MediaResource media) {
        return new MediaView(media.getId(), media.getResourceType(), media.getContentType(), media.getFileSize(),
                media.getWidth(), media.getHeight(), media.getDurationMs(),
                media.getRelativePath() != null ? mediaStorage.getUrl(media.getRelativePath()) : media.getUrl());
    }

    private BusinessException stateConflict() {
        return new BusinessException("STATE_CONFLICT", HttpStatus.CONFLICT, "视频当前状态不允许执行该操作");
    }
}
