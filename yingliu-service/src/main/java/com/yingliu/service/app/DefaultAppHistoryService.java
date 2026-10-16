package com.yingliu.service.app;

import com.yingliu.service.app.mapper.UserVideoHistoryMapper;
import com.yingliu.service.content.shared.CursorPage;
import com.yingliu.domain.interaction.UserVideoHistory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("!test")
public class DefaultAppHistoryService implements AppHistoryService {
    private static final int MAX_LIMIT = 50;

    private final UserVideoHistoryMapper historyMapper;

    public DefaultAppHistoryService(UserVideoHistoryMapper historyMapper) {
        this.historyMapper = historyMapper;
    }

    @Override
    @Transactional
    public void add(long userId, long videoId, int progressMs) {
        historyMapper.upsert(userId, videoId, Math.max(0, progressMs));
    }

    @Override
    public CursorPage<HistoryEntry> list(long userId, String cursor, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), MAX_LIMIT);
        Long cursorVideoId = parseCursor(cursor);
        List<UserVideoHistory> rows = historyMapper.selectRecent(userId, cursorVideoId, pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        List<UserVideoHistory> page = hasNext ? rows.subList(0, pageSize) : rows;
        List<HistoryEntry> entries = page.stream()
                .map(h -> new HistoryEntry(h.getVideoId(),
                        h.getLastViewedAt() == null ? 0 : h.getLastViewedAt()
                                .atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
                        h.getLastProgressMs() == null ? 0 : h.getLastProgressMs()))
                .toList();
        String nextCursor = entries.isEmpty() ? null : String.valueOf(entries.get(entries.size() - 1).videoId());
        return new CursorPage<>(entries, hasNext ? nextCursor : null);
    }

    @Override
    @Transactional
    public void clear(long userId) {
        historyMapper.clearAll(userId);
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            long id = Long.parseLong(cursor);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            throw new com.yingliu.common.exception.BusinessException("INVALID_CURSOR",
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "分页游标不合法");
        }
    }
}
