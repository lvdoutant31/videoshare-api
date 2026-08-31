package com.yingliu.service.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface DashboardService {
    Summary summary();
    Trend trend(String range);
    List<Todo> todos();
    Storage storage();

    record Summary(String updatedAt, List<Metric> metrics, List<RecentVideo> recentVideos) { }
    record Metric(String key, String label, String value, String changeLabel, String tone) { }
    record Trend(String range, List<TrendPoint> points) { }
    record TrendPoint(String date, long plays, long interactions) { }
    record Todo(String key, String label, long count, String tone) { }
    record Storage(String mount, int usedPercent, String backupStatus, String lastBackupAt) { }
    record RecentVideo(String id, String title, String author, String status, Long playCount, LocalDateTime publishedAt) { }
}
