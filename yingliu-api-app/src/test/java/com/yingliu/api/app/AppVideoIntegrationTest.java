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
        jdbcTemplate.update("DELETE FROM video_tag_map");
        jdbcTemplate.update("DELETE FROM video_stats");
        jdbcTemplate.update("DELETE FROM video");
        jdbcTemplate.update("DELETE FROM media_resource");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("INSERT INTO sys_user (id, nickname, status, deleted, created_at, updated_at) VALUES (?, ?, 'NORMAL', 0, NOW(3), NOW(3))", AUTHOR_ID, "公开作者");
        insertMedia(ORIGINAL_MEDIA_ID, "VIDEO_ORIGINAL", "READY", "origin.mp4", "video/mp4", 60000);
        insertMedia(COVER_MEDIA_ID, "COVER", "READY", "cover.jpg", "image/jpeg", null);
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
