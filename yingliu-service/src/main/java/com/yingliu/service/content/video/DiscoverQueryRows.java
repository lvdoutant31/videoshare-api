package com.yingliu.service.content.video;

/** MyBatis projections used by the public discovery dashboard queries. */
public final class DiscoverQueryRows {
    private DiscoverQueryRows() { }

    public static class Summary {
        private long totalVideos;
        private long totalWatchTimeMs;
        private long effectivePlayCount;
        private long averageWatchTimeMs;
        private long todayNewCount;
        public long getTotalVideos() { return totalVideos; }
        public void setTotalVideos(long totalVideos) { this.totalVideos = totalVideos; }
        public long getTotalWatchTimeMs() { return totalWatchTimeMs; }
        public void setTotalWatchTimeMs(long value) { this.totalWatchTimeMs = value; }
        public long getEffectivePlayCount() { return effectivePlayCount; }
        public void setEffectivePlayCount(long value) { this.effectivePlayCount = value; }
        public long getAverageWatchTimeMs() { return averageWatchTimeMs; }
        public void setAverageWatchTimeMs(long value) { this.averageWatchTimeMs = value; }
        public long getTodayNewCount() { return todayNewCount; }
        public void setTodayNewCount(long todayNewCount) { this.todayNewCount = todayNewCount; }
    }

    public static class CategoryStat {
        private long id;
        private String name;
        private long videoCount;
        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public long getVideoCount() { return videoCount; }
        public void setVideoCount(long videoCount) { this.videoCount = videoCount; }
    }

    public static class DurationStats {
        private long underTenSeconds;
        private long tenToThirtySeconds;
        private long thirtyToSixtySeconds;
        private long oneToThreeMinutes;
        private long overThreeMinutes;
        public long getUnderTenSeconds() { return underTenSeconds; }
        public void setUnderTenSeconds(long value) { this.underTenSeconds = value; }
        public long getTenToThirtySeconds() { return tenToThirtySeconds; }
        public void setTenToThirtySeconds(long value) { this.tenToThirtySeconds = value; }
        public long getThirtyToSixtySeconds() { return thirtyToSixtySeconds; }
        public void setThirtyToSixtySeconds(long value) { this.thirtyToSixtySeconds = value; }
        public long getOneToThreeMinutes() { return oneToThreeMinutes; }
        public void setOneToThreeMinutes(long value) { this.oneToThreeMinutes = value; }
        public long getOverThreeMinutes() { return overThreeMinutes; }
        public void setOverThreeMinutes(long value) { this.overThreeMinutes = value; }
    }

    public static class TagOption {
        private long id;
        private String name;
        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class WatchTime {
        private long videoId;
        private long watchTimeMs;
        public long getVideoId() { return videoId; }
        public void setVideoId(long videoId) { this.videoId = videoId; }
        public long getWatchTimeMs() { return watchTimeMs; }
        public void setWatchTimeMs(long watchTimeMs) { this.watchTimeMs = watchTimeMs; }
    }

    public static class LikeCount {
        private long videoId;
        private long likeCount;
        public long getVideoId() { return videoId; }
        public void setVideoId(long videoId) { this.videoId = videoId; }
        public long getLikeCount() { return likeCount; }
        public void setLikeCount(long likeCount) { this.likeCount = likeCount; }
    }
}
