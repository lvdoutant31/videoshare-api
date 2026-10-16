package com.yingliu.service.app;

import com.yingliu.service.content.shared.CursorPage;

/**
 * 观看历史（API §2.5）：有效播放写入、列表、清空。
 */
public interface AppHistoryService {

    /** 写入/更新观看历史（幂等 upsert） */
    void add(long userId, long videoId, int progressMs);

    /** 历史列表（游标分页，video_id 倒序） */
    CursorPage<HistoryEntry> list(long userId, String cursor, int limit);

    /** 清空可恢复展示历史 */
    void clear(long userId);

    record HistoryEntry(long videoId, long lastViewedAtEpochMillis, int progressMs) { }
}
