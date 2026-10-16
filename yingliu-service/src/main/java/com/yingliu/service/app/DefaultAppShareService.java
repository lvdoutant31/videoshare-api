package com.yingliu.service.app;

import com.yingliu.common.exception.BusinessException;
import com.yingliu.domain.share.VideoShareEvent;
import com.yingliu.domain.share.VideoShareInbox;
import com.yingliu.domain.user.AppUser;
import com.yingliu.media.storage.MediaStorage;
import com.yingliu.service.app.mapper.AppUserMapper;
import com.yingliu.service.app.mapper.VideoShareEventMapper;
import com.yingliu.service.app.mapper.VideoShareInboxMapper;
import com.yingliu.service.content.video.mapper.MediaResourceMapper;
import com.yingliu.service.content.video.mapper.VideoMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Profile("!test")
public class DefaultAppShareService implements AppShareService {
    private static final int MAX_RECIPIENTS = 20;
    private static final int MAX_SEARCH_LIMIT = 50;

    private final VideoShareInboxMapper inboxMapper;
    private final VideoShareEventMapper shareEventMapper;
    private final AppUserMapper userMapper;
    private final VideoMapper videoMapper;
    private final MediaResourceMapper mediaMapper;
    private final MediaStorage mediaStorage;

    public DefaultAppShareService(VideoShareInboxMapper inboxMapper,
                                  VideoShareEventMapper shareEventMapper,
                                  AppUserMapper userMapper,
                                  VideoMapper videoMapper,
                                  MediaResourceMapper mediaMapper,
                                  MediaStorage mediaStorage) {
        this.inboxMapper = inboxMapper;
        this.shareEventMapper = shareEventMapper;
        this.userMapper = userMapper;
        this.videoMapper = videoMapper;
        this.mediaMapper = mediaMapper;
        this.mediaStorage = mediaStorage;
    }

    @Override
    public List<RecipientView> searchRecipients(String q, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), MAX_SEARCH_LIMIT);
        List<AppUser> users = userMapper.selectByKeyword(q, pageSize);
        Map<Long, String> avatars = avatarUrls(users.stream().map(AppUser::getAvatarMediaId).filter(java.util.Objects::nonNull).toList());
        return users.stream()
                .map(u -> new RecipientView(String.valueOf(u.getId()), u.getNickname(),
                        u.getAvatarMediaId() == null ? null : avatars.get(u.getAvatarMediaId())))
                .toList();
    }

    @Override
    public List<RecipientView> recentRecipients(long senderId, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), MAX_SEARCH_LIMIT);
        List<VideoShareInbox> rows = inboxMapper.selectRecentRecipients(senderId, pageSize);
        List<Long> userIds = rows.stream().map(VideoShareInbox::getRecipientId).toList();
        if (userIds.isEmpty()) return List.of();
        Map<Long, AppUser> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        Map<Long, String> avatars = avatarUrls(users.values().stream().map(AppUser::getAvatarMediaId).filter(java.util.Objects::nonNull).toList());
        return rows.stream()
                .map(r -> {
                    AppUser user = users.get(r.getRecipientId());
                    return user == null ? null : new RecipientView(String.valueOf(user.getId()), user.getNickname(),
                            user.getAvatarMediaId() == null ? null : avatars.get(user.getAvatarMediaId()));
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    @Transactional
    public InternalShareResult sendInternal(long senderId, long videoId, List<String> recipientIds) {
        if (recipientIds == null || recipientIds.isEmpty()) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "请选择接收人");
        }
        if (recipientIds.size() > MAX_RECIPIENTS) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "单次最多发送给 " + MAX_RECIPIENTS + " 名用户");
        }
        if (videoMapper.selectPublicById(videoId) == null) {
            throw new BusinessException("VIDEO_NOT_FOUND", HttpStatus.NOT_FOUND, "视频不存在或不可转发");
        }
        if (userMapper.countNormal(senderId) == 0) {
            throw new BusinessException("ACCOUNT_SUSPENDED", HttpStatus.FORBIDDEN, "账号不可用，无法发送");
        }

        List<Long> parsed;
        try {
            parsed = recipientIds.stream().distinct().map(Long::parseLong).toList();
        } catch (NumberFormatException e) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "接收人 ID 不合法");
        }

        long batchIdValue = System.currentTimeMillis();
        String batchId = String.valueOf(batchIdValue);
        List<InternalShareResult.ItemResult> results = new ArrayList<>();
        for (Long recipientId : parsed) {
            if (userMapper.countNormal(recipientId) == 0) {
                results.add(new InternalShareResult.ItemResult(String.valueOf(recipientId), false, null, "RECIPIENT_SUSPENDED"));
                continue;
            }
            // 幂等去重：同发送人同视频同接收人已有送达记录则复用
            VideoShareInbox existing = inboxMapper.selectRecentDelivered(senderId, recipientId, videoId);
            if (existing != null) {
                results.add(new InternalShareResult.ItemResult(String.valueOf(recipientId), true, String.valueOf(existing.getId()), null));
                continue;
            }
            VideoShareInbox inbox = new VideoShareInbox();
            inbox.setBatchId(batchIdValue);
            inbox.setSenderId(senderId);
            inbox.setRecipientId(recipientId);
            inbox.setVideoId(videoId);
            inbox.setStatus("DELIVERED");
            inbox.setSentAt(LocalDateTime.now());
            inboxMapper.insert(inbox);
            results.add(new InternalShareResult.ItemResult(String.valueOf(recipientId), true, String.valueOf(inbox.getId()), null));
        }
        return new InternalShareResult(batchId, results);
    }

    @Override
    public List<InboxView> inbox(long userId) {
        List<VideoShareInbox> rows = inboxMapper.selectInbox(userId);
        return toViews(rows);
    }

    @Override
    @Transactional
    public InboxView inboxDetail(long userId, long inboxId) {
        VideoShareInbox inbox = inboxMapper.selectById(inboxId);
        if (inbox == null || inbox.getRecipientId() != userId) {
            throw new BusinessException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "分享不存在");
        }
        // 打开前实时校验视频可见性（下架/删除 → 置 INVALID）
        if ("DELIVERED".equals(inbox.getStatus()) && videoMapper.selectPublicById(inbox.getVideoId()) == null) {
            inbox.setStatus("INVALID");
            inbox.setInvalidReason("VIDEO_OFF_SHELF");
            inboxMapper.updateById(inbox);
        }
        inboxMapper.markOpened(inboxId, userId);
        if (inbox.getOpenedAt() == null) {
            inbox.setOpenedAt(LocalDateTime.now());
        }
        return toViews(List.of(inbox)).get(0);
    }

    @Override
    public void recordShareEvent(Long userId, Long videoId, String channel, String eventType, String traceId) {
        if (!List.of("WECHAT", "WECOM", "INTERNAL", "COPY_LINK", "SYSTEM").contains(channel)) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "分享渠道不合法");
        }
        if (!List.of("ENTRY_CLICKED", "CHANNEL_SELECTED", "INTERNAL_SENT", "LANDING_OPENED").contains(eventType)) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "分享事件类型不合法");
        }
        VideoShareEvent event = new VideoShareEvent();
        event.setVideoId(videoId);
        event.setUserId(userId);
        event.setChannel(channel);
        event.setEventType(eventType);
        event.setTraceId(traceId);
        event.setCreatedAt(LocalDateTime.now());
        shareEventMapper.insert(event);
    }

    private List<InboxView> toViews(List<VideoShareInbox> rows) {
        if (rows.isEmpty()) return List.of();
        Set<Long> senderIds = rows.stream().map(VideoShareInbox::getSenderId).collect(Collectors.toSet());
        Map<Long, AppUser> senders = userMapper.selectBatchIds(senderIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        Map<Long, String> avatars = avatarUrls(senders.values().stream().map(AppUser::getAvatarMediaId).filter(java.util.Objects::nonNull).toList());
        return rows.stream().map(r -> {
            AppUser sender = senders.get(r.getSenderId());
            return new InboxView(String.valueOf(r.getId()),
                    String.valueOf(r.getSenderId()),
                    sender == null ? "未知用户" : sender.getNickname(),
                    sender == null || sender.getAvatarMediaId() == null ? null : avatars.get(sender.getAvatarMediaId()),
                    String.valueOf(r.getVideoId()),
                    r.getStatus(),
                    String.valueOf(r.getSentAt()),
                    r.getOpenedAt() == null ? null : String.valueOf(r.getOpenedAt()));
        }).toList();
    }

    private Map<Long, String> avatarUrls(List<Long> mediaIds) {
        if (mediaIds.isEmpty()) return Map.of();
        return mediaMapper.selectBatchIds(mediaIds).stream()
                .filter(m -> m.getRelativePath() != null)
                .collect(Collectors.toMap(m -> m.getId(), m -> mediaStorage.getUrl(m.getRelativePath()), (a, b) -> a));
    }
}
