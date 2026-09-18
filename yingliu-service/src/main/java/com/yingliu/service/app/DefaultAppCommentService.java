package com.yingliu.service.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.domain.comment.VideoComment;
import com.yingliu.domain.content.video.MediaResource;
import com.yingliu.domain.content.video.Video;
import com.yingliu.domain.governance.ContentReport;
import com.yingliu.domain.governance.SysSensitiveWord;
import com.yingliu.domain.interaction.UserCommentLike;
import com.yingliu.domain.user.AppUser;
import com.yingliu.media.storage.MediaStorage;
import com.yingliu.service.app.mapper.AppUserMapper;
import com.yingliu.service.app.mapper.ContentReportMapper;
import com.yingliu.service.app.mapper.SysSensitiveWordMapper;
import com.yingliu.service.app.mapper.UserCommentLikeMapper;
import com.yingliu.service.app.mapper.VideoCommentMapper;
import com.yingliu.service.content.shared.CursorPage;
import com.yingliu.service.content.video.mapper.MediaResourceMapper;
import com.yingliu.service.content.video.mapper.VideoMapper;
import com.yingliu.service.content.video.mapper.VideoStatsMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Profile("!test")
public class DefaultAppCommentService implements AppCommentService {
    private static final Logger log = LoggerFactory.getLogger(DefaultAppCommentService.class);
    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int HOT_SCORE_LIKE_BONUS = 100;
    private static final int MAX_LIST_LIMIT = 50;

    private final VideoCommentMapper commentMapper;
    private final UserCommentLikeMapper commentLikeMapper;
    private final AppUserMapper userMapper;
    private final MediaResourceMapper mediaMapper;
    private final MediaStorage mediaStorage;
    private final VideoMapper videoMapper;
    private final VideoStatsMapper statsMapper;
    private final ContentReportMapper reportMapper;
    private final SysSensitiveWordMapper sensitiveWordMapper;

    /** 进程内敏感词缓存（管理端变更后需刷新；首期启动加载） */
    private List<SysSensitiveWord> sensitiveWords = List.of();

    public DefaultAppCommentService(VideoCommentMapper commentMapper,
                                    UserCommentLikeMapper commentLikeMapper,
                                    AppUserMapper userMapper,
                                    MediaResourceMapper mediaMapper,
                                    MediaStorage mediaStorage,
                                    VideoMapper videoMapper,
                                    VideoStatsMapper statsMapper,
                                    ContentReportMapper reportMapper,
                                    SysSensitiveWordMapper sensitiveWordMapper) {
        this.commentMapper = commentMapper;
        this.commentLikeMapper = commentLikeMapper;
        this.userMapper = userMapper;
        this.mediaMapper = mediaMapper;
        this.mediaStorage = mediaStorage;
        this.videoMapper = videoMapper;
        this.statsMapper = statsMapper;
        this.reportMapper = reportMapper;
        this.sensitiveWordMapper = sensitiveWordMapper;
    }

    @PostConstruct
    void loadSensitiveWords() {
        try {
            sensitiveWords = sensitiveWordMapper.selectEnabled();
        } catch (Exception e) {
            log.warn("敏感词加载失败，评论将直接通过: {}", e.getMessage());
        }
    }

    @Override
    public CursorPage<CommentView> listRootComments(long videoId, String sort, String cursor, int limit, Long viewerId) {
        requireVisibleVideo(videoId);
        int pageSize = Math.min(Math.max(limit, 1), MAX_LIST_LIMIT);
        Long cursorId = parseCursor(cursor);
        List<VideoComment> rows = commentMapper.selectRootPage(videoId, "LATEST".equals(sort) ? "LATEST" : "HOT", cursorId, pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        List<VideoComment> page = hasNext ? rows.subList(0, pageSize) : rows;
        List<CommentView> views = toViews(page, viewerId, false);
        String nextCursor = page.isEmpty() ? null : String.valueOf(page.get(page.size() - 1).getId());
        return new CursorPage<>(views, hasNext ? nextCursor : null);
    }

    @Override
    public List<CommentView> listReplies(long rootCommentId, Long viewerId) {
        List<VideoComment> replies = commentMapper.selectReplies(rootCommentId);
        return toViews(replies, viewerId, false);
    }

    @Override
    @Transactional
    public PublishResult publish(long userId, long videoId, String content, Long replyToCommentId) {
        requireNotMuted(userId);
        requireVisibleVideo(videoId);
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "评论内容需为 1-1000 字符");
        }

        VideoComment parent = null;
        Long rootCommentId = 0L;
        if (replyToCommentId != null) {
            parent = commentMapper.selectById(replyToCommentId);
            if (parent == null || parent.getDeletedAt() != null || !"NORMAL".equals(parent.getStatus())) {
                throw new BusinessException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "被回复的评论不存在");
            }
            rootCommentId = parent.getRootCommentId() != null && parent.getRootCommentId() != 0
                    ? parent.getRootCommentId() : parent.getId();
        }

        String status = sensitiveCheck(content);
        VideoComment comment = new VideoComment();
        comment.setVideoId(videoId);
        comment.setUserId(userId);
        comment.setRootCommentId(rootCommentId);
        comment.setReplyToCommentId(replyToCommentId);
        comment.setContent(content.trim());
        comment.setStatus(status);
        comment.setRiskLevel("NORMAL".equals(status) ? "NONE" : "LOW");
        comment.setLikeCount(0L);
        comment.setReplyCount(0L);
        comment.setHotScore(BigDecimal.valueOf(Instant.now().getEpochSecond()));
        commentMapper.insert(comment);

        if (rootCommentId != 0L && parent != null) {
            commentMapper.incrementReplyCount(rootCommentId);
        }
        if (rootCommentId == 0L) {
            statsMapper.adjustCommentCount(videoId, 1);
        }
        if (!"NORMAL".equals(status)) {
            log.info("评论进入待审 videoId={} userId={} status={}", videoId, userId, status);
        }
        return new PublishResult(String.valueOf(comment.getId()), status, "NORMAL".equals(status) ? null : "评论正在审核");
    }

    @Override
    @Transactional
    public TargetState setCommentLike(long userId, long commentId, boolean active) {
        VideoComment comment = requireVisibleComment(commentId);
        boolean existed = commentLikeMapper.selectCount(new LambdaQueryWrapper<UserCommentLike>()
                .eq(UserCommentLike::getUserId, userId).eq(UserCommentLike::getCommentId, commentId)) > 0;
        if (active && !existed) {
            UserCommentLike like = new UserCommentLike();
            like.setUserId(userId);
            like.setCommentId(commentId);
            like.setCreatedAt(LocalDateTime.now());
            try {
                commentLikeMapper.insert(like);
            } catch (DuplicateKeyException ignored) {
                // 唯一约束兜底
            }
            commentMapper.adjustLikeCount(commentId, 1);
            commentMapper.updateHotScore(commentId, HOT_SCORE_LIKE_BONUS);
        } else if (!active && existed) {
            commentLikeMapper.delete(new LambdaQueryWrapper<UserCommentLike>()
                    .eq(UserCommentLike::getUserId, userId).eq(UserCommentLike::getCommentId, commentId));
            commentMapper.adjustLikeCount(commentId, -1);
            commentMapper.updateHotScore(commentId, -HOT_SCORE_LIKE_BONUS);
        }
        long count = comment.getLikeCount() == null ? 0 : comment.getLikeCount() + (active && !existed ? 1 : 0);
        return new TargetState(active, Math.max(0, count));
    }

    @Override
    @Transactional
    public void deleteOwned(long userId, long commentId) {
        VideoComment comment = commentMapper.selectById(commentId);
        if (comment == null || !Objects.equals(comment.getUserId(), userId) || comment.getDeletedAt() != null) {
            throw new BusinessException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "评论不存在或无权删除");
        }
        commentMapper.deleteOwned(commentId, userId);
        if (comment.getRootCommentId() == null || comment.getRootCommentId() == 0) {
            commentMapper.hideRepliesOf(commentId, userId);
            statsMapper.adjustCommentCount(comment.getVideoId(), -1);
        }
    }

    @Override
    @Transactional
    public void report(long userId, long commentId, String reasonCode, String description) {
        VideoComment comment = requireVisibleComment(commentId);
        insertReport(userId, "COMMENT", commentId, reasonCode, description,
                "{\"content\":\"" + escapeJson(comment.getContent()) + "\"}");
    }

    @Override
    @Transactional
    public void reportVideo(long userId, long videoId, String reasonCode, String description) {
        requireVisibleVideo(videoId);
        insertReport(userId, "VIDEO", videoId, reasonCode, description, null);
    }

    @Override
    @Transactional
    public void reportUser(long userId, long targetUserId, String reasonCode, String description) {
        if (userMapper.countNormal(targetUserId) == 0) {
            throw new BusinessException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "用户不存在");
        }
        insertReport(userId, "USER", targetUserId, reasonCode, description, null);
    }

    private void insertReport(long userId, String targetType, long targetId, String reasonCode,
                              String description, String snapshot) {
        ContentReport report = new ContentReport();
        report.setReporterId(userId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReasonCode(reasonCode);
        report.setDescription(description);
        report.setTargetSnapshot(snapshot);
        report.setStatus("PENDING");
        report.setRiskLevel("NONE");
        reportMapper.insert(report);
    }

    private String sensitiveCheck(String content) {
        if (sensitiveWords.isEmpty()) return "NORMAL";
        for (SysSensitiveWord word : sensitiveWords) {
            if (content.contains(word.getWord())) {
                return "BLOCK".equals(word.getAction()) ? "HIDDEN" : "PENDING";
            }
        }
        return "NORMAL";
    }

    private void requireNotMuted(long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("AUTH_REQUIRED", HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if ("MUTED".equals(user.getStatus())
                && (user.getMuteUntil() == null || user.getMuteUntil().isAfter(LocalDateTime.now()))) {
            throw new BusinessException("ACCOUNT_MUTED", HttpStatus.FORBIDDEN, "账号已被禁言，无法评论");
        }
    }

    private void requireVisibleVideo(long videoId) {
        Video video = videoMapper.selectPublicById(videoId);
        if (video == null) {
            throw new BusinessException("VIDEO_NOT_FOUND", HttpStatus.NOT_FOUND, "视频不存在或不可访问");
        }
    }

    private VideoComment requireVisibleComment(long commentId) {
        VideoComment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeletedAt() != null || !"NORMAL".equals(comment.getStatus())) {
            throw new BusinessException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "评论不存在");
        }
        return comment;
    }

    private List<CommentView> toViews(List<VideoComment> comments, Long viewerId, boolean withReplies) {
        if (comments.isEmpty()) return List.of();
        List<Long> userIds = comments.stream().map(VideoComment::getUserId).distinct().toList();
        Map<Long, AppUser> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        Map<Long, Long> avatarMediaIds = users.values().stream()
                .filter(u -> u.getAvatarMediaId() != null)
                .collect(Collectors.toMap(AppUser::getId, AppUser::getAvatarMediaId, (a, b) -> a));
        Map<Long, String> avatarUrls = avatarMediaIds.isEmpty() ? Map.of()
                : mediaMapper.selectBatchIds(avatarMediaIds.values()).stream()
                .collect(Collectors.toMap(MediaResource::getId, m -> mediaStorage.getUrl(m.getRelativePath())));
        List<Long> ids = comments.stream().map(VideoComment::getId).toList();
        java.util.Set<Long> likedIds = viewerId == null ? java.util.Set.of()
                : commentLikeMapper.selectList(new LambdaQueryWrapper<UserCommentLike>()
                        .eq(UserCommentLike::getUserId, viewerId).in(UserCommentLike::getCommentId, ids))
                .stream().map(UserCommentLike::getCommentId).collect(Collectors.toSet());

        return comments.stream().map(c -> toView(c, users.get(c.getUserId()), avatarUrls, likedIds, viewerId)).toList();
    }

    private CommentView toView(VideoComment c, AppUser user, Map<Long, String> avatarUrls,
                               java.util.Set<Long> likedIds, Long viewerId) {
        String avatarUrl = user != null && user.getAvatarMediaId() != null
                ? avatarUrls.getOrDefault(user.getAvatarMediaId(), null) : null;
        boolean root = c.getRootCommentId() == null || c.getRootCommentId() == 0;
        return new CommentView(String.valueOf(c.getId()), String.valueOf(c.getVideoId()),
                new CommentUser(String.valueOf(c.getUserId()), user == null ? "未知用户" : user.getNickname(), avatarUrl),
                root ? null : String.valueOf(c.getRootCommentId()),
                c.getReplyToCommentId() == null ? null : String.valueOf(c.getReplyToCommentId()),
                c.getContent(), c.getStatus(),
                c.getLikeCount() == null ? 0 : c.getLikeCount(),
                c.getReplyCount() == null ? 0 : c.getReplyCount(),
                likedIds.contains(c.getId()),
                viewerId != null && Objects.equals(viewerId, c.getUserId()),
                String.valueOf(c.getCreatedAt()),
                null);
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

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
