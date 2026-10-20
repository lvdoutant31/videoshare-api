package com.yingliu.service.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcDashboardServiceTest {
    @Mock
    private JdbcTemplate jdbc;

    private JdbcDashboardService service;

    @BeforeEach
    void setUp() {
        service = new JdbcDashboardService(jdbc);
    }

    @Test
    void summaryUsesActionableLiveMetricFields() {
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        DashboardService.Summary summary = service.summary();

        assertEquals(List.of("plays", "newUsers", "publishedVideos", "interactions"),
                summary.metrics().stream().map(DashboardService.Metric::key).toList());
        assertFalse(summary.metrics().stream().anyMatch(metric -> metric.changeLabel().isBlank()));
    }

    @Test
    void recentVideoQueriesKeepLimitOutsideInSubqueriesForMySqlCompatibility() throws Exception {
        for (String fieldName : List.of("VIDEO_STATS_SQL", "VIDEO_TRANSCODE_SQL")) {
            var field = JdbcDashboardService.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            String sql = (String) field.get(null);

            assertTrue(sql.contains("JOIN (SELECT id FROM video WHERE deleted = 0 ORDER BY updated_at DESC LIMIT 10)"));
            assertFalse(sql.contains(" IN (SELECT"));
        }
    }

    @Test
    void todosExposeNavigationTargets() {
        List<DashboardService.Todo> todos = service.todos();

        assertEquals("/reviews", todos.get(0).to());
        assertEquals("/reports", todos.get(1).to());
        assertEquals("storageWarning", todos.get(2).key());
        assertEquals(3, todos.size());
    }

    @Test
    void storageDoesNotReportMediaRecordUpdatesAsBackups() {
        DashboardService.Storage storage = service.storage();

        assertEquals("unknown", storage.backupStatus());
        assertNull(storage.lastBackupAt());
    }
}
