package com.yingliu.service.app;

import java.util.List;

/**
 * 站内转发与分享事件（API §2.5）。
 */
public interface AppShareService {

    /** 站内接收人搜索（昵称/用户 ID） */
    List<RecipientView> searchRecipients(String q, int limit);

    /** 最近转发对象 */
    List<RecipientView> recentRecipients(long senderId, int limit);

    /** 站内转发：逐接收人结果；同一发送人短时间重复发送幂等去重（recipientIds 为字符串 ID，契约） */
    InternalShareResult sendInternal(long senderId, long videoId, List<String> recipientIds);

    /** 收件箱列表 */
    List<InboxView> inbox(long userId);

    /** 收件箱卡片详情；打开前实时校验视频可见性，记录打开事件 */
    InboxView inboxDetail(long userId, long inboxId);

    /** 记录外部分享事件（入口点击/渠道选择/落地页访问） */
    void recordShareEvent(Long userId, Long videoId, String channel, String eventType, String traceId);

    record RecipientView(String id, String nickname, String avatarUrl) { }

    record InternalShareResult(String batchId, List<ItemResult> results) {
        public record ItemResult(String recipientId, boolean success, String inboxId, String code) { }
    }

    record InboxView(String id, String senderId, String senderNickname, String senderAvatarUrl, String videoId,
                     String status, String sentAt, String openedAt) { }
}
