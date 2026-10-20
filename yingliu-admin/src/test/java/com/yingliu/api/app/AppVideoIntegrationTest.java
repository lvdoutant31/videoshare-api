package com.yingliu.api.app;

import com.yingliu.YingliuApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = YingliuApplication.class, properties = {
        "spring.flyway.enabled=true",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,cn.dev33.satoken.dao.SaTokenDaoRedisJackson"
})
@AutoConfigureMockMvc
class AppVideoIntegrationTest {
    private static final long AUTHOR_ID = 2001L;
    private static final long ORIGINAL_MEDIA_ID = 2002L;
    private static final long COVER_MEDIA_ID = 2003L;
    private static final long CATEGORY_ID = 2004L;
    private static final long TAG_ID = 2005L;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("yingliu-db")
            .withUsername("yingliu_test")
            .withPassword("yingliu_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_video_like");
        jdbcTemplate.update("DELETE FROM video_category_map");
        jdbcTemplate.update("DELETE FROM video_tag_map");
        jdbcTemplate.update("DELETE FROM video_watch_event");
        jdbcTemplate.update("DELETE FROM video_watch_session");
        jdbcTemplate.update("DELETE FROM video_watch_daily");
        jdbcTemplate.update("DELETE FROM video_stats");
        jdbcTemplate.update("DELETE FROM video");
        jdbcTemplate.update("DELETE FROM media_resource");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("DELETE FROM content_category");
        jdbcTemplate.update("DELETE FROM content_tag");
        jdbcTemplate.update("INSERT INTO sys_user (id, nickname, status, deleted, created_at, updated_at) VALUES (?, ?, 'NORMAL', 0, NOW(3), NOW(3))", AUTHOR_ID, "公开作者");
        insertMedia(ORIGINAL_MEDIA_ID, "VIDEO_ORIGINAL", "READY", "origin.mp4", "video/mp4", 60000);
        insertMedia(COVER_MEDIA_ID, "COVER", "READY", "cover.jpg", "image/jpeg", null);
    }

    @Test
    void migrationEscapesLegacyDescriptionsAndPreservesLineBreaks() throws Exception {
        jdbcTemplate.execute("CREATE TABLE legacy_content_migration (id BIGINT PRIMARY KEY, description VARCHAR(2000), original_media_id BIGINT UNSIGNED NOT NULL)");
        try {
            jdbcTemplate.update("INSERT INTO legacy_content_migration VALUES (1, ?, 2002)", "简介 <文字> & 符号\r\n下一行");
            String script;
            try (var input = getClass().getResourceAsStream("/db/migration/V202610091200__support_mixed_content.sql")) {
                script = new String(java.util.Objects.requireNonNull(input).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .replace("ALTER TABLE video", "ALTER TABLE legacy_content_migration")
                        .replace("UPDATE video", "UPDATE legacy_content_migration");
            }
            try (var connection = java.util.Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                        new org.springframework.core.io.ByteArrayResource(script.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
            org.junit.jupiter.api.Assertions.assertEquals("<p>简介 &lt;文字&gt; &amp; 符号<br>下一行</p>",
                    jdbcTemplate.queryForObject("SELECT description FROM legacy_content_migration WHERE id = 1", String.class));
            jdbcTemplate.update("UPDATE legacy_content_migration SET original_media_id = NULL WHERE id = 1");
        } finally { jdbcTemplate.execute("DROP TABLE legacy_content_migration"); }
    }

    @Test
    void articlesAndTextAreIncludedInFeedDiscoverAndDetailWithoutVideoMedia() throws Exception {
        long articleId = insertVideo("多图正文", "PUBLISHED", "PUBLIC", "2026-09-28 09:00:00.000");
        jdbcTemplate.update("UPDATE video SET original_media_id = NULL, cover_media_id = NULL, description = ? WHERE id = ?",
                "<p><strong>正文</strong></p><img src=\"https://example.com/first.png\"><img src=\"https://example.com/second.png\">", articleId);
        mockMvc.perform(get("/api/v1/app/feed/recommend"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(articleId)))
                .andExpect(jsonPath("$.data.items[0].videoUrl").isEmpty())
                .andExpect(jsonPath("$.data.items[0].cover.coverUrl").value("https://example.com/first.png"));
        mockMvc.perform(get("/api/v1/app/videos/" + articleId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.description").value(org.hamcrest.Matchers.containsString("<strong>正文</strong>")));
        mockMvc.perform(get("/api/v1/app/discover"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.totalVideos").value(1));
        jdbcTemplate.update("UPDATE video SET description = '<p>纯文字</p>' WHERE id = ?", articleId);
        mockMvc.perform(get("/api/v1/app/videos/" + articleId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.cover").isEmpty());
    }

    @Test
    void openingArticleDetailCountsEachVisitWithoutCountingListReadsOrVideoOpens() throws Exception {
        long articleId = insertVideo("图文阅读计数", "PUBLISHED", "PUBLIC", "2026-10-14 09:00:00.000");
        jdbcTemplate.update("UPDATE video SET original_media_id = NULL, cover_media_id = NULL WHERE id = ?", articleId);
        long videoId = insertVideo("视频仍按时长计数", "PUBLISHED", "PUBLIC", "2026-10-14 08:00:00.000");

        mockMvc.perform(get("/api/v1/app/feed/recommend")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/app/discover")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/app/videos/{videoId}", articleId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stats.playCount").value(0));
        mockMvc.perform(get("/api/v1/app/content/{contentId}", videoId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stats.playCount").value(0));
        for (int visits = 1; visits <= 2; visits++) {
            mockMvc.perform(get("/api/v1/app/content/{contentId}", articleId))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.data.stats.playCount").value(visits));
        }
        org.junit.jupiter.api.Assertions.assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT play_count FROM video_stats WHERE video_id = ?", Long.class, articleId));
        org.junit.jupiter.api.Assertions.assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT effective_play_count FROM video_watch_daily WHERE video_id = ? AND metric_date = ?",
                Long.class, articleId, java.time.LocalDate.now(java.time.ZoneOffset.UTC)));
        org.junit.jupiter.api.Assertions.assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT watch_time_ms + complete_count FROM video_watch_daily WHERE video_id = ?", Long.class, articleId));
        org.junit.jupiter.api.Assertions.assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_watch_session", Long.class));

        jdbcTemplate.update("UPDATE video SET visibility = 'PRIVATE' WHERE id = ?", articleId);
        mockMvc.perform(get("/api/v1/app/content/{contentId}", articleId)).andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT play_count FROM video_stats WHERE video_id = ?", Long.class, articleId));
    }

    @Test
    void discoverReturnsRealDashboardAndFilteredVideos() throws Exception {
        long videoId = insertVideo("折弯机安全操作指南", "PUBLISHED", "PUBLIC", "2026-09-28 09:00:00.000");
        jdbcTemplate.update("UPDATE video SET published_at = NOW(3) WHERE id = ?", videoId);
        jdbcTemplate.update("INSERT INTO video_stats (video_id, play_count, like_count, favorite_count, comment_count, updated_at) VALUES (?, 1200, 20, 6, 3, NOW(3))", videoId);
        jdbcTemplate.update("INSERT INTO content_category (id, name, sort, status, deleted, created_at, updated_at) VALUES (?, '安全培训', 1, 'ENABLED', 0, NOW(3), NOW(3))", CATEGORY_ID);
        jdbcTemplate.update("INSERT INTO content_tag (id, name, status, heat_score, deleted, created_at, updated_at) VALUES (?, '入门', 'ENABLED', 0, 0, NOW(3), NOW(3))", TAG_ID);
        jdbcTemplate.update("INSERT INTO video_category_map (video_id, category_id) VALUES (?, ?)", videoId, CATEGORY_ID);
        jdbcTemplate.update("INSERT INTO video_tag_map (video_id, tag_id) VALUES (?, ?)", videoId, TAG_ID);
        jdbcTemplate.update("INSERT INTO video_watch_session (play_session_id, video_id, anonymous_session_id, watch_time_ms, max_position_ms, duration_ms, effective_play, completed, started_at, updated_at) VALUES ('10000000-0000-0000-0000-000000000001', ?, '20000000-0000-0000-0000-000000000001', 5000, 5000, 60000, 1, 0, NOW(3), NOW(3)), ('10000000-0000-0000-0000-000000000002', ?, '20000000-0000-0000-0000-000000000002', 40000, 55000, 60000, 1, 1, NOW(3), NOW(3))", videoId, videoId);
        jdbcTemplate.update("INSERT INTO video_watch_daily (metric_date, video_id, effective_play_count, watch_time_ms, complete_count, calculated_at) VALUES (CURRENT_DATE, ?, 2, 45000, 1, NOW(3))", videoId);

        mockMvc.perform(get("/api/v1/app/discover")
                        .param("categoryId", String.valueOf(CATEGORY_ID))
                        .param("tagId", String.valueOf(TAG_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalVideos").value(1))
                .andExpect(jsonPath("$.data.summary.totalWatchTimeMs").value(45000))
                .andExpect(jsonPath("$.data.summary.effectivePlayCount").value(2))
                .andExpect(jsonPath("$.data.summary.averageWatchTimeMs").value(22500))
                .andExpect(jsonPath("$.data.summary.todayNewCount").value(1))
                .andExpect(jsonPath("$.data.categories[0].name").value("安全培训"))
                .andExpect(jsonPath("$.data.durationStats[0].sessionCount").value(1))
                .andExpect(jsonPath("$.data.tags[0].name").value("入门"))
                .andExpect(jsonPath("$.data.ranking[0].title").value("折弯机安全操作指南"))
                .andExpect(jsonPath("$.data.videos[0].stats.watchTimeMs").value(45000))
                .andExpect(jsonPath("$.data.videos[0].categories[0].id").value(String.valueOf(CATEGORY_ID)))
                .andExpect(jsonPath("$.data.videos[0].tags[0].id").value(String.valueOf(TAG_ID)));
    }

    @Test
    void searchPrioritizesTitleRelevanceAndSupportsSortingAndPages() throws Exception {
        long exact = insertVideo("设备点检", "PUBLISHED", "PUBLIC", "2026-01-01 09:00:00.000");
        long prefix = insertVideo("设备点检流程", "PUBLISHED", "PUBLIC", "2026-02-01 09:00:00.000");
        long contains = insertVideo("日常设备点检流程", "PUBLISHED", "PUBLIC", "2026-03-01 09:00:00.000");
        long tagOnly = insertVideo("热门培训", "PUBLISHED", "PUBLIC", "2026-04-01 09:00:00.000");
        jdbcTemplate.update("INSERT INTO content_tag (id, name, status, heat_score, deleted, created_at, updated_at) VALUES (?, '设备点检', 'ENABLED', 0, 0, NOW(3), NOW(3))", TAG_ID);
        jdbcTemplate.update("INSERT INTO video_tag_map (video_id, tag_id) VALUES (?, ?)", tagOnly, TAG_ID);
        jdbcTemplate.update("INSERT INTO video_watch_daily (metric_date, video_id, effective_play_count, watch_time_ms, complete_count, calculated_at) VALUES (UTC_DATE(), ?, 10000, 0, 0, NOW(3))", tagOnly);

        mockMvc.perform(get("/api/v1/app/discover").param("keyword", "设备点检").param("limit", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.videos[0].id").value(String.valueOf(exact)))
                .andExpect(jsonPath("$.data.videos[1].id").value(String.valueOf(prefix)))
                .andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.hasMore").value(true));
        mockMvc.perform(get("/api/v1/app/discover").param("keyword", "设备点检").param("limit", "2").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.videos[0].id").value(String.valueOf(contains)))
                .andExpect(jsonPath("$.data.videos[1].id").value(String.valueOf(tagOnly)))
                .andExpect(jsonPath("$.data.hasMore").value(false));
        for (String sort : java.util.List.of("LATEST", "HOT")) {
            mockMvc.perform(get("/api/v1/app/discover").param("keyword", "设备点检").param("sort", sort))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.videos[0].id").value(String.valueOf(tagOnly)));
        }
        long literal = insertVideo("100%点检", "PUBLISHED", "PUBLIC", "2026-04-01 09:00:00.000");
        mockMvc.perform(get("/api/v1/app/discover").param("keyword", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.videos.length()").value(1))
                .andExpect(jsonPath("$.data.videos[0].id").value(String.valueOf(literal)));
        mockMvc.perform(get("/api/v1/app/discover").param("page", "0")).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/app/discover").param("sort", "invalid")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void hotRankingDecaysRecentActivityAndIgnoresHistoricalTotals() throws Exception {
        long recent = insertVideo("今天播放", "PUBLISHED", "PUBLIC", "2026-01-01 09:00:00.000");
        long older = insertVideo("六天前播放", "PUBLISHED", "PUBLIC", "2026-01-01 09:00:00.000");
        long expired = insertVideo("历史高播放", "PUBLISHED", "PUBLIC", "2026-01-01 09:00:00.000");
        long liked = insertVideo("今日点赞", "PUBLISHED", "PUBLIC", "2026-01-01 09:00:00.000");
        jdbcTemplate.update("INSERT INTO video_watch_daily (metric_date, video_id, effective_play_count, watch_time_ms, complete_count, calculated_at) VALUES (UTC_DATE(), ?, 2, 0, 0, NOW(3)), (DATE_SUB(UTC_DATE(), INTERVAL 6 DAY), ?, 2, 0, 0, NOW(3)), (DATE_SUB(UTC_DATE(), INTERVAL 7 DAY), ?, 1000000, 0, 0, NOW(3))", recent, older, expired);
        jdbcTemplate.update("INSERT INTO video_stats (video_id, play_count, like_count, favorite_count, comment_count, updated_at) VALUES (?, 1000000, 1000000, 1000000, 1000000, NOW(3))", expired);
        jdbcTemplate.update("INSERT INTO user_video_like (user_id, video_id, created_at) VALUES (?, ?, NOW(3))", AUTHOR_ID, liked);
        mockMvc.perform(get("/api/v1/app/discover").param("sort", "HOT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.videos[0].id").value(String.valueOf(liked)))
                .andExpect(jsonPath("$.data.videos[1].id").value(String.valueOf(recent)))
                .andExpect(jsonPath("$.data.videos[2].id").value(String.valueOf(older)))
                .andExpect(jsonPath("$.data.videos[3].id").value(String.valueOf(expired)));
    }

    @Test
    void homepageReservesOneSpotForFreshPublicContentAndFallsBackToHotContent() throws Exception {
        for (int i = 0; i < 5; i++) {
            long id = insertVideo("热门 " + i, "PUBLISHED", "PUBLIC", "2026-01-01 09:00:00.000");
            jdbcTemplate.update("INSERT INTO video_watch_daily (metric_date, video_id, effective_play_count, watch_time_ms, complete_count, calculated_at) VALUES (UTC_DATE(), ?, ?, 0, 0, NOW(3))", id, 1000 + i);
        }
        long fresh = insertVideo("近期新内容", "PUBLISHED", "PUBLIC", "2026-01-01 09:00:00.000");
        jdbcTemplate.update("UPDATE video SET published_at = NOW(3) WHERE id = ?", fresh);
        long hidden = insertVideo("私密新内容", "PUBLISHED", "PRIVATE", "2026-01-01 09:00:00.000");
        jdbcTemplate.update("UPDATE video SET published_at = NOW(3) WHERE id = ?", hidden);
        mockMvc.perform(get("/api/v1/app/discover"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.ranking.length()").value(5))
                .andExpect(jsonPath("$.data.ranking[4].id").value(String.valueOf(fresh)))
                .andExpect(jsonPath("$.data.ranking[0].title").value("热门 4"));
        jdbcTemplate.update("UPDATE video SET publish_status = 'OFF_SHELF' WHERE id = ?", fresh);
        mockMvc.perform(get("/api/v1/app/discover"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.ranking.length()").value(5))
                .andExpect(jsonPath("$.data.ranking[4].title").value("热门 0"));
    }

    @Test
    void listsAndReadsOnlyPublicPublishedVideos() throws Exception {
        long publicId = insertVideo("公开视频", "PUBLISHED", "PUBLIC", "2026-08-28 10:00:00.000");
        jdbcTemplate.update("INSERT INTO video_stats (video_id, play_count, like_count, favorite_count, comment_count, updated_at) VALUES (?, 10, 2, 3, 4, NOW(3))", publicId);
        insertVideo("私密视频", "PUBLISHED", "PRIVATE", "2026-08-28 11:00:00.000");
        insertVideo("定时视频", "SCHEDULED", "PUBLIC", "2026-08-28 12:00:00.000");

        mockMvc.perform(get("/api/v1/app/feed/recommend"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(publicId)))
                .andExpect(jsonPath("$.data.items[0].stats.playCount").value(10))
                .andExpect(jsonPath("$.data.items[0].cover.relativePath").doesNotExist());

        mockMvc.perform(get("/api/v1/app/videos/{videoId}", publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("公开视频"))
                .andExpect(jsonPath("$.data.viewerState.liked").value(false));
    }

    @Test
    void hidesPrivateVideosAndRejectsInvalidCursor() throws Exception {
        long privateId = insertVideo("私密视频", "PUBLISHED", "PRIVATE", "2026-08-28 10:00:00.000");
        mockMvc.perform(get("/api/v1/app/videos/{videoId}", privateId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VIDEO_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/app/feed/recommend").param("cursor", "not-a-cursor"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mockMvc.perform(get("/api/v1/app/feed/recommend").param("limit", "20"))
                .andExpect(status().isOk());
    }

    private long insertVideo(String title, String publishStatus, String visibility, String publishedAt) {
        long id = 3000L + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM video", Integer.class);
        jdbcTemplate.update("INSERT INTO video (id, author_id, title, original_media_id, cover_media_id, audit_status, publish_status, visibility, risk_level, manual_weight, published_at, current_version, deleted, created_by, updated_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'APPROVED', ?, ?, 'NONE', 0, ?, 1, 0, 1, 1, NOW(3), NOW(3))",
                id, AUTHOR_ID, title, ORIGINAL_MEDIA_ID, COVER_MEDIA_ID, publishStatus, visibility, publishedAt);
        return id;
    }

    private void insertMedia(long id, String resourceType, String state, String path, String contentType, Integer duration) {
        jdbcTemplate.update("INSERT INTO media_resource (id, resource_type, storage_state, relative_path, content_type, file_size, sha256, duration_ms, backup_status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 100, ?, ?, 'PENDING', NOW(3), NOW(3))",
                id, resourceType, state, path, contentType, String.format("%064d", id), duration);
    }
}
