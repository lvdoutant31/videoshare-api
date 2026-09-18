package com.yingliu.api.app;

import com.yingliu.YingliuApplication;
import com.yingliu.service.app.support.VerificationCodeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * App 域核心链路集成测试（§2.1 登录 / §2.3 互动 / §2.4 评论 / 举报）。
 * 验证码固定 123456（sms-send-enabled=false），验证码存储用内存实现（无 Redis）。
 * 数据库：优先 TEST_MYSQL_URL 环境变量（本地库），否则用 Testcontainers MySQL。
 */
@SpringBootTest(classes = YingliuApplication.class, properties = {
        "spring.flyway.enabled=true",
        "yingliu.app.sms-per-minute-limit=1000",
        "yingliu.app.phone-login-enabled=true",
        "yingliu.wecom.mock-enabled=true",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,cn.dev33.satoken.dao.SaTokenDaoRedisJackson"
})
@AutoConfigureMockMvc
@EnabledIf("databaseAvailable")
class AppApiIntegrationTest {
    private static final long AUTHOR_ID = 2001L;
    private static final long PEER_ID = 2002L;
    private static final long ORIGINAL_MEDIA_ID = 2101L;
    private static final long COVER_MEDIA_ID = 2102L;

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("yingliu-db")
            .withUsername("yingliu_test")
            .withPassword("yingliu_test_password");

    // 在 JUnit 条件判断后才启动容器，避免无 Docker 时类初始化直接失败。
    static boolean databaseAvailable() {
        String override = System.getenv("TEST_MYSQL_URL");
        return (override != null && !override.isBlank()) || DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        String override = System.getenv("TEST_MYSQL_URL");
        if (override != null && !override.isBlank()) {
            registry.add("spring.datasource.url", () -> override);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_MYSQL_USER", "root"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_MYSQL_PASSWORD", ""));
        } else {
            MYSQL.start();
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private VerificationCodeStore codeStore;

    @BeforeEach
    void setUp() {
        // 先删引用 sys_user/video/comment 的表，再删主表（外键顺序）
        jdbcTemplate.update("DELETE FROM sys_user_wecom_identity");
        jdbcTemplate.update("DELETE FROM sys_user_agreement");
        jdbcTemplate.update("DELETE FROM sys_verification_record");
        jdbcTemplate.update("DELETE FROM user_comment_like");
        jdbcTemplate.update("DELETE FROM video_comment");
        jdbcTemplate.update("DELETE FROM user_video_like");
        jdbcTemplate.update("DELETE FROM user_video_favorite");
        jdbcTemplate.update("DELETE FROM user_video_history");
        jdbcTemplate.update("DELETE FROM video_share_inbox");
        jdbcTemplate.update("DELETE FROM video_share_event");
        jdbcTemplate.update("DELETE FROM user_follow");
        jdbcTemplate.update("DELETE FROM video_tag_map");
        jdbcTemplate.update("DELETE FROM video_stats");
        jdbcTemplate.update("DELETE FROM video");
        jdbcTemplate.update("DELETE FROM media_resource");
        jdbcTemplate.update("DELETE FROM content_report");
        jdbcTemplate.update("DELETE FROM sys_user");
        try {
            jdbcTemplate.update("DELETE FROM analytics_event_"
                    + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")));
        } catch (Exception ignored) {
            // 当月埋点表可能尚未创建
        }
        insertUser(AUTHOR_ID, "公开作者");
        insertUser(PEER_ID, "同行用户");
        insertMedia(ORIGINAL_MEDIA_ID, "VIDEO_ORIGINAL", "READY", "origin.mp4", "video/mp4", 60000);
        insertMedia(COVER_MEDIA_ID, "COVER", "READY", "cover.jpg", "image/jpeg", null);
    }

    @Test
    void phoneLoginCreatesSessionAndMeWorks() throws Exception {
        String token = login("13800138000");

        mockMvc.perform(get("/api/v1/app/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("用户8000"))
                .andExpect(jsonPath("$.data.wecomBound").value(false));

        mockMvc.perform(patch("/api/v1/app/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"新昵称\",\"bio\":\"新简介\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.bio").value("新简介"));

        mockMvc.perform(get("/api/v1/app/me/agreements").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].currentVersion").value("2026-08"));

        mockMvc.perform(post("/api/v1/app/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/app/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void wrongCodeRejected() throws Exception {
        mockMvc.perform(post("/api/v1/app/auth/phone/send-code")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"13800138001\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/app/auth/phone/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800138001\",\"code\":\"000000\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void likeAndFavoriteAreIdempotent() throws Exception {
        long videoId = insertVideo("点赞幂等视频");
        String token = login("13800138002");

        mockMvc.perform(put("/api/v1/app/videos/{id}/like", videoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.count").value(1));

        // 重复点赞不重复计数（幂等）
        mockMvc.perform(put("/api/v1/app/videos/{id}/like", videoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        // 取消点赞
        mockMvc.perform(put("/api/v1/app/videos/{id}/like", videoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        // 收藏并出现在 me/favorites
        mockMvc.perform(put("/api/v1/app/videos/{id}/favorite", videoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));
        mockMvc.perform(get("/api/v1/app/me/favorites").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(videoId)));
    }

    @Test
    void decrementCountersClampDriftedUnsignedValuesAtZero() throws Exception {
        long videoId = insertVideo("统计数防下溢视频");
        String token = login("13800138012");
        long viewerId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE id NOT IN (?, ?) ORDER BY created_at DESC LIMIT 1",
                Long.class, AUTHOR_ID, PEER_ID);

        // 关系记录存在但聚合统计意外漂移为 0 时，取消点赞/收藏仍应成功并保持为 0。
        mockMvc.perform(put("/api/v1/app/videos/{id}/like", videoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE video_stats SET like_count = 0 WHERE video_id = ?", videoId);
        mockMvc.perform(put("/api/v1/app/videos/{id}/like", videoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        mockMvc.perform(put("/api/v1/app/videos/{id}/favorite", videoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE video_stats SET favorite_count = 0 WHERE video_id = ?", videoId);
        mockMvc.perform(put("/api/v1/app/videos/{id}/favorite", videoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        // 关注双方的聚合统计发生漂移时，取关也不能触发 UNSIGNED 下溢。
        mockMvc.perform(put("/api/v1/app/users/{id}/follow", AUTHOR_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE sys_user SET following_count = 0 WHERE id = ?", viewerId);
        jdbcTemplate.update("UPDATE sys_user SET follower_count = 0 WHERE id = ?", AUTHOR_ID);
        mockMvc.perform(put("/api/v1/app/users/{id}/follow", AUTHOR_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        // 评论点赞数和视频评论数采用相同的归零保护。
        mockMvc.perform(post("/api/v1/app/videos/{id}/comments", videoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"统计数下溢回归评论\"}"))
                .andExpect(status().isOk());
        long commentId = jdbcTemplate.queryForObject(
                "SELECT id FROM video_comment WHERE content = '统计数下溢回归评论'", Long.class);
        mockMvc.perform(put("/api/v1/app/comments/{id}/like", commentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE video_comment SET like_count = 0 WHERE id = ?", commentId);
        mockMvc.perform(put("/api/v1/app/comments/{id}/like", commentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        jdbcTemplate.update("UPDATE video_stats SET comment_count = 0 WHERE video_id = ?", videoId);
        mockMvc.perform(delete("/api/v1/app/comments/{id}", commentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        Integer commentCount = jdbcTemplate.queryForObject(
                "SELECT comment_count FROM video_stats WHERE video_id = ?", Integer.class, videoId);
        org.junit.jupiter.api.Assertions.assertEquals(0, commentCount);
    }

    @Test
    void followUpdatesCountsAndLists() throws Exception {
        long followedVideoId = insertVideo("关注作者的视频");
        long otherVideoId = insertVideo("未关注作者的视频");
        jdbcTemplate.update("UPDATE video SET author_id = ? WHERE id = ?", PEER_ID, otherVideoId);
        String token = login("13800138003");

        mockMvc.perform(get("/api/v1/app/feed/following").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));

        mockMvc.perform(put("/api/v1/app/users/{id}/follow", AUTHOR_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        mockMvc.perform(get("/api/v1/app/me/following").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(String.valueOf(AUTHOR_ID)))
                .andExpect(jsonPath("$.data[0].nickname").value("公开作者"));

        mockMvc.perform(get("/api/v1/app/feed/following").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(followedVideoId)))
                .andExpect(jsonPath("$.data.items[0].author.following").value(true));

        mockMvc.perform(get("/api/v1/app/users/{id}", AUTHOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("公开作者"))
                .andExpect(jsonPath("$.data.followerCount").value(1));
    }

    @Test
    void commentPublishListReplyLikeDelete() throws Exception {
        long videoId = insertVideo("评论视频");
        String authorToken = login("13800138004");
        String peerToken = login("13800138005");

        // 发表一级评论
        mockMvc.perform(post("/api/v1/app/videos/{id}/comments", videoId)
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"第一条评论\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NORMAL"));

        // 列表可见（游客也可读）
        mockMvc.perform(get("/api/v1/app/videos/{id}/comments", videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].content").value("第一条评论"))
                .andExpect(jsonPath("$.data.items[0].mine").value(false));

        // 回复
        long rootId = jdbcTemplate.queryForObject("SELECT id FROM video_comment WHERE content = '第一条评论'", Long.class);
        mockMvc.perform(post("/api/v1/app/videos/{id}/comments", videoId)
                        .header("Authorization", "Bearer " + peerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"回复一下\",\"replyToCommentId\":" + rootId + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/app/comments/{id}/replies", rootId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].content").value("回复一下"));

        // 点赞评论
        long replyId = jdbcTemplate.queryForObject("SELECT id FROM video_comment WHERE content = '回复一下'", Long.class);
        mockMvc.perform(put("/api/v1/app/comments/{id}/like", replyId)
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        // 他人无权删除我的评论
        mockMvc.perform(delete("/api/v1/app/comments/{id}", replyId)
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isNotFound());

        // 本人删除
        mockMvc.perform(delete("/api/v1/app/comments/{id}", replyId)
                        .header("Authorization", "Bearer " + peerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/app/comments/{id}/replies", rootId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void reportVideoAndComment() throws Exception {
        long videoId = insertVideo("举报视频");
        String token = login("13800138006");

        mockMvc.perform(post("/api/v1/app/videos/{id}/report", videoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"SPAM\",\"description\":\"垃圾内容\"}"))
                .andExpect(status().isOk());

        Integer reportCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM content_report WHERE target_type = 'VIDEO' AND target_id = ?", Integer.class, videoId);
        org.junit.jupiter.api.Assertions.assertEquals(1, reportCount);
    }

    @Test
    void historyAddListAndClear() throws Exception {
        long videoId = insertVideo("历史视频");
        String token = login("13800138007");

        mockMvc.perform(post("/api/v1/app/me/history")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoId\":\"" + videoId + "\",\"progressMs\":15000}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/app/me/history").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].videoId").value(String.valueOf(videoId)))
                .andExpect(jsonPath("$.data.items[0].progressMs").value(15000))
                .andExpect(jsonPath("$.data.items[0].video.title").value("历史视频"));

        mockMvc.perform(delete("/api/v1/app/me/history").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/app/me/history").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void internalShareDeliversToInboxAndDeduplicates() throws Exception {
        long videoId = insertVideo("转发视频");
        String senderToken = login("13800138008");
        String receiverToken = login("13800138009");
        long receiverId = extractUserId(receiverToken);

        mockMvc.perform(post("/api/v1/app/videos/{id}/shares/internal", videoId)
                        .header("Authorization", "Bearer " + senderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientIds\":[\"" + receiverId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].success").value(true))
                .andExpect(jsonPath("$.data.results[0].inboxId").isNotEmpty());

        // 重复发送幂等：返回同一 inboxId，不重复插入
        mockMvc.perform(post("/api/v1/app/videos/{id}/shares/internal", videoId)
                        .header("Authorization", "Bearer " + senderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientIds\":[\"" + receiverId + "\"]}"))
                .andExpect(status().isOk());
        Integer inboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_share_inbox WHERE recipient_id = ?", Integer.class, receiverId);
        org.junit.jupiter.api.Assertions.assertEquals(1, inboxCount);

        // 接收人收件箱可见并打开
        mockMvc.perform(get("/api/v1/app/share-inbox").header("Authorization", "Bearer " + receiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("DELIVERED"));
        long inboxId = jdbcTemplate.queryForObject("SELECT id FROM video_share_inbox WHERE recipient_id = ?", Long.class, receiverId);
        mockMvc.perform(get("/api/v1/app/share-inbox/{id}", inboxId).header("Authorization", "Bearer " + receiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openedAt").isNotEmpty());

        // 接收人搜索
        mockMvc.perform(get("/api/v1/app/share-recipients").param("q", "公开作者")
                        .header("Authorization", "Bearer " + senderToken))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nickname").value("公开作者"));
    }

    @Test
    void shareEventsAndAnalyticsAreRecorded() throws Exception {
        long videoId = insertVideo("埋点视频");
        String token = login("13800138010");

        mockMvc.perform(post("/api/v1/app/videos/{id}/share-events", videoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"WECHAT\",\"eventType\":\"CHANNEL_SELECTED\"}"))
                .andExpect(status().isOk());
        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_share_event WHERE video_id = ? AND channel = 'WECHAT'", Integer.class, videoId);
        org.junit.jupiter.api.Assertions.assertEquals(1, eventCount);

        mockMvc.perform(post("/api/v1/analytics/events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[{\"eventName\":\"video_expose\",\"occurredAt\":\"2026-09-02T00:00:00Z\","
                                + "\"anonymousSessionId\":\"session-1\",\"videoId\":" + videoId + ","
                                + "\"pageSource\":\"recommend_feed\",\"properties\":{\"position\":1}}]}"))
                .andExpect(status().isOk());
        String table = "analytics_event_" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        Integer analyticsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE event_name = 'video_expose'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, analyticsCount);

        // 非法事件名整批拒绝
        mockMvc.perform(post("/api/v1/analytics/events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[{\"eventName\":\"hack_event\",\"anonymousSessionId\":\"s\"}]}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void wecomLoginBindsBrowserStateAndReusesEncryptedIdentity() throws Exception {
        String state = wecomState();
        var cookie = new jakarta.servlet.http.Cookie("yingliu-wecom-state", state);
        String body = "{\"code\":\"mock-code\",\"state\":\"" + state + "\"}";
        mockMvc.perform(post("/api/v1/app/auth/wecom/callback").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        var first = mockMvc.perform(post("/api/v1/app/auth/wecom/callback").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.wecomBound").value(true))
                .andExpect(jsonPath("$.data.user.nickname").value("企业开发成员"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String userId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(first).at("/data/user/id").asText();
        mockMvc.perform(post("/api/v1/app/auth/wecom/callback").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("WECOM_STATE_INVALID"));
        String second = wecomState();
        mockMvc.perform(post("/api/v1/app/auth/wecom/callback")
                        .cookie(new jakarta.servlet.http.Cookie("yingliu-wecom-state", second))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"mock-code\",\"state\":\"" + second + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.id").value(userId));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_wecom_identity WHERE user_id = ?", Integer.class, userId));
        byte[] encrypted = jdbcTemplate.queryForObject("SELECT wecom_user_id_ciphertext FROM sys_user_wecom_identity WHERE user_id = ?", byte[].class, userId);
        org.junit.jupiter.api.Assertions.assertEquals("dev-member-001", com.yingliu.common.security.PhoneCrypto.decrypt(encrypted, "local-wecom-identity-key-0000000000"));
        jdbcTemplate.update("UPDATE sys_user SET status = 'BANNED' WHERE id = ?", userId);
        String blocked = wecomState();
        mockMvc.perform(post("/api/v1/app/auth/wecom/callback")
                        .cookie(new jakarta.servlet.http.Cookie("yingliu-wecom-state", blocked))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"mock-code\",\"state\":\"" + blocked + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wecomRejectsBadCodeExpiredStateAndUnboundIdentity() throws Exception {
        String state = wecomState();
        String requestBody = "{\"code\":\"invalid-code\",\"state\":\"" + state + "\"}";
        mockMvc.perform(post("/api/v1/app/auth/wecom/callback")
                        .cookie(new jakarta.servlet.http.Cookie("yingliu-wecom-state", "wrong-state"))
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/app/auth/wecom/callback")
                        .cookie(new jakarta.servlet.http.Cookie("yingliu-wecom-state", state))
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("WECOM_CODE_INVALID"));
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user_wecom_identity", Integer.class));
        String expired = wecomState();
        codeStore.delete("yingliu:wecom:state:" + expired);
        mockMvc.perform(post("/api/v1/app/auth/wecom/callback")
                        .cookie(new jakarta.servlet.http.Cookie("yingliu-wecom-state", expired))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"mock-code\",\"state\":\"" + expired + "\"}"))
                .andExpect(status().isUnauthorized());
        String valid = wecomState();
        mockMvc.perform(post("/api/v1/app/auth/wecom/callback")
                        .cookie(new jakarta.servlet.http.Cookie("yingliu-wecom-state", valid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"mock-code\",\"state\":\"" + valid + "\"}"))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE sys_user_wecom_identity SET bind_status = 'UNBOUND'");
        String unbound = wecomState();
        mockMvc.perform(post("/api/v1/app/auth/wecom/callback")
                        .cookie(new jakarta.servlet.http.Cookie("yingliu-wecom-state", unbound))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"mock-code\",\"state\":\"" + unbound + "\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WECOM_BINDING_INACTIVE"));
    }

    private String wecomState() throws Exception {
        String json = mockMvc.perform(get("/api/v1/app/auth/wecom/authorize-url"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).at("/data/state").asText();
    }

    // ---- helpers ----

    private String login(String phone) throws Exception {
        mockMvc.perform(post("/api/v1/app/auth/phone/send-code")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/v1/app/auth/phone/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"code\":\"123456\",\"agreementVersion\":\"2026-08\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        // 从 JSON 中提取 accessToken
        int start = body.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
        int end = body.indexOf("\"", start);
        return body.substring(start, end);
    }

    /** 从 /me 响应提取当前用户 ID（契约：ID 为字符串） */
    private long extractUserId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/app/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int start = body.indexOf("\"id\":\"") + "\"id\":\"".length();
        int end = body.indexOf("\"", start);
        return Long.parseLong(body.substring(start, end));
    }

    private long insertVideo(String title) {
        long id = 3000L + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM video", Integer.class);
        jdbcTemplate.update("INSERT INTO video (id, author_id, title, original_media_id, cover_media_id, audit_status, publish_status, visibility, risk_level, manual_weight, published_at, current_version, deleted, created_by, updated_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'APPROVED', 'PUBLISHED', 'PUBLIC', 'NONE', 0, NOW(3), 1, 0, 1, 1, NOW(3), NOW(3))",
                id, AUTHOR_ID, title, ORIGINAL_MEDIA_ID, COVER_MEDIA_ID);
        jdbcTemplate.update("INSERT INTO video_stats (video_id, play_count, like_count, favorite_count, comment_count, updated_at) VALUES (?, 0, 0, 0, 0, NOW(3))", id);
        return id;
    }

    private void insertUser(long id, String nickname) {
        jdbcTemplate.update("INSERT INTO sys_user (id, nickname, status, violation_count, like_received_count, following_count, follower_count, deleted, created_at, updated_at) VALUES (?, ?, 'NORMAL', 0, 0, 0, 0, 0, NOW(3), NOW(3))",
                id, nickname);
    }

    private void insertMedia(long id, String resourceType, String state, String path, String contentType, Integer duration) {
        jdbcTemplate.update("INSERT INTO media_resource (id, resource_type, storage_state, relative_path, content_type, file_size, sha256, duration_ms, backup_status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 100, ?, ?, 'PENDING', NOW(3), NOW(3))",
                id, resourceType, state, path, contentType, String.format("%064d", id), duration);
    }
}
