package com.yingliu.service.app;

import com.yingliu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AppAnalyticsEventsTest {
    private AppAnalyticsService.EventInput event(String name, Map<String, Object> properties) {
        return new AppAnalyticsService.EventInput("event-id", null, name, "2026-10-19T00:00:00Z", "session-id",
                null, null, null, "discover", "0.1.0", null, null, properties);
    }

    @Test
    void acceptsSearchAndPublishingEventsWithoutRecordingVideoWatchTime() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        VideoWatchMetricsService watchMetrics = mock(VideoWatchMetricsService.class);
        var service = new AppAnalyticsService(jdbc, watchMetrics);
        var events = List.of("search_submitted", "search_results_loaded", "search_empty", "search_failed",
                        "search_result_clicked", "publish_started", "publish_submitted", "publish_failed")
                .stream().map(name -> event(name, Map.of("contentType", "VIDEO", "queryLength", 2))).toList();
        assertDoesNotThrow(() -> service.receive(events));
        verify(jdbc, times(8)).update(anyString(), any(Object[].class));
        verifyNoInteractions(watchMetrics);
    }

    @Test
    void continuesToRejectSensitivePropertiesAndUnknownEvents() {
        var service = new AppAnalyticsService(mock(JdbcTemplate.class), mock(VideoWatchMetricsService.class));
        assertThrows(BusinessException.class, () -> service.receive(List.of(event("search_submitted", Map.of("phone", "redacted")))));
        assertThrows(BusinessException.class, () -> service.receive(List.of(event("unregistered_event", Map.of()))));
    }
}
