package com.yingliu.service.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface AdminDashboardService {
    DashboardSnapshot snapshot(LocalDate date);
    Summary summary(LocalDate date);
    Trends trends(LocalDate date, int days);
    Todos todos();
    Storage storage();

    record DashboardSnapshot(long totalVideos, long publishedVideos, long pendingComments,
                             long pendingReports, long totalUsers, long plays, long likes,
                             List<DailyMetric> dailyMetrics) { }
    record DailyMetric(LocalDate date, String name, BigDecimal value) { }
    record Summary(String updatedAt, List<Metric> metrics, List<RecentVideo> recentVideos) { }
    record Metric(String key, String label, String value, String changeLabel, String tone) { }
    record RecentVideo(String id, String title, String author, String status, Long playCount, String publishedAt) { }
    record Trends(List<TrendPoint> points) { }
    record TrendPoint(String date, long plays, long interactions) { }
    record Todos(List<Todo> items) { }
    record Todo(String key, String label, long count, String tone) { }
    record Storage(String mount, double usedPercent, String backupStatus, String lastBackupAt) { }
}
