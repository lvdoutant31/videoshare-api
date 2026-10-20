package com.yingliu.api.app.content.video;

import com.yingliu.service.content.video.VideoAdminService;

import java.time.LocalDateTime;
import java.util.List;

public final class AppMyContentDtos {
    private AppMyContentDtos() { }

    public static Detail detail(VideoAdminService.VideoDetail value, String videoUrl, long likeCount, long playCount) {
        return new Detail(String.valueOf(value.id()), value.authorNickname(), value.title(), value.description(), videoUrl,
                value.auditStatus(), value.publishStatus(), value.currentVersion(),
                value.categories().stream().map(item -> new Option(String.valueOf(item.id()), item.name())).toList(),
                value.tags().stream().map(item -> new Option(String.valueOf(item.id()), item.name())).toList(),
                media(value.originalMedia()), media(value.coverMedia()), value.createdAt(), value.updatedAt(), likeCount, playCount);
    }

    private static Media media(VideoAdminService.MediaView value) {
        return value == null ? null : new Media(String.valueOf(value.id()), value.resourceType(),
                value.contentType(), value.fileSize(), value.width(), value.height(), value.durationMs(), value.url());
    }

    // Snowflake IDs exceed JavaScript's safe integer range, so API identifiers must be strings.
    public record Detail(String id, String authorNickname, String title, String description, String videoUrl,
                         String auditStatus, String publishStatus,
                         int currentVersion, List<Option> categories, List<Option> tags,
                         Media originalMedia, Media coverMedia, LocalDateTime createdAt, LocalDateTime updatedAt,
                         long likeCount, long playCount) { }
    public record Option(String id, String name) { }
    public record Media(String id, String resourceType, String contentType, long fileSize, Integer width,
                        Integer height, Integer durationMs, String url) { }
}
