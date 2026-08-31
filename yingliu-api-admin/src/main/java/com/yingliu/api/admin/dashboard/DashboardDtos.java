package com.yingliu.api.admin.dashboard;

import com.yingliu.service.dashboard.DashboardService;
import java.util.List;

public final class DashboardDtos {
 private DashboardDtos(){}
 public record Summary(String updatedAt,List<Metric> metrics,List<RecentVideo> recentVideos){}
 public record Metric(String key,String label,String value,String changeLabel,String tone){}
 public record Trend(String range,List<TrendPoint> points){}
 public record TrendPoint(String date,long plays,long interactions){}
 public record Todos(List<Todo> items){}
 public record Todo(String key,String label,long count,String tone){}
 public record Storage(String mount,int usedPercent,String backupStatus,String lastBackupAt){}
 static Summary summary(DashboardService.Summary s){return new Summary(s.updatedAt(),s.metrics().stream().map(x->new Metric(x.key(),x.label(),x.value(),x.changeLabel(),x.tone())).toList(),s.recentVideos().stream().map(x->new RecentVideo(x.id(),x.title(),x.author(),x.status(),x.playCount(),x.publishedAt()==null?null:x.publishedAt().toString())).toList());}
 static Trend trend(DashboardService.Trend t){return new Trend(t.range(),t.points().stream().map(x->new TrendPoint(x.date(),x.plays(),x.interactions())).toList());}
 static Todo todo(DashboardService.Todo x){return new Todo(x.key(),x.label(),x.count(),x.tone());}
 static Storage storage(DashboardService.Storage x){return new Storage(x.mount(),x.usedPercent(),x.backupStatus(),x.lastBackupAt());}
 public record RecentVideo(String id,String title,String author,String status,Long playCount,String publishedAt){}
}
