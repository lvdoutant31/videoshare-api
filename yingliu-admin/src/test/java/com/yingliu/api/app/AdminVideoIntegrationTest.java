package com.yingliu.api.app;

import com.yingliu.YingliuApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingliu.api.admin.security.AdminActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = {YingliuApplication.class, AdminVideoIntegrationTest.ActorConfiguration.class}, properties = {
        "spring.flyway.enabled=true",
        "yingliu.admin.auth-bypass-admin-id=1001",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,cn.dev33.satoken.dao.SaTokenDaoRedisJackson"
})
@AutoConfigureMockMvc(addFilters = false)
class AdminVideoIntegrationTest {
    private static final long ADMIN_ID = 1001L;
    private static final long AUTHOR_ID = 1002L;
    private static final long ORIGINAL_MEDIA_ID = 1003L;
    private static final long COVER_MEDIA_ID = 1004L;
    private static final long CATEGORY_ID = 1005L;
    private static final long TAG_ID = 1006L;
    private static final long SECOND_ADMIN_ID = 1007L;

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


    @Test
    void creationUsesCurrentAuthorWithOrWithoutClientAuthor() throws Exception {
        for (String author : new String[] { "\"authorId\":\"999999\",", "" }) {
            mockMvc.perform(post("/api/v1/admin/videos").contentType("application/json")
                            .content("{" + author + "\"originalMediaId\":\"1003\",\"title\":\"固定作者\",\"categoryIds\":[\"1005\"],\"tagIds\":[]}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.authorId").value("1002"));
        }
    }

    @Test
    void creationBindsOneProfileAndReusesItForUnboundCurrentAccount() throws Exception {
        jdbcTemplate.update("UPDATE sys_admin_user SET user_id = NULL WHERE id = ?", ADMIN_ID);
        String authorId = null;
        for (int i = 0; i < 2; i++) {
            String response = mockMvc.perform(post("/api/v1/admin/videos").contentType("application/json")
                            .content("{\"authorId\":\"1002\",\"originalMediaId\":\"1003\",\"title\":\"固定作者\",\"categoryIds\":[\"1005\"],\"tagIds\":[]}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.authorNickname").value("test-admin"))
                    .andReturn().getResponse().getContentAsString();
            String actualAuthorId = objectMapper.readTree(response).path("data").path("authorId").asText();
            if (authorId != null) org.junit.jupiter.api.Assertions.assertEquals(authorId, actualAuthorId);
            authorId = actualAuthorId;
        }
        org.junit.jupiter.api.Assertions.assertNotEquals("1002", authorId);
        org.junit.jupiter.api.Assertions.assertEquals(Long.valueOf(authorId),
                jdbcTemplate.queryForObject("SELECT user_id FROM sys_admin_user WHERE id = ?", Long.class, ADMIN_ID));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE nickname = 'test-admin'", Integer.class));
    }


    @Test
    void concurrentFirstUploadsShareOneAuthorProfile() throws Exception {
        jdbcTemplate.update("UPDATE sys_admin_user SET user_id = NULL WHERE id = ?", ADMIN_ID);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            java.util.concurrent.Callable<Long> resolve = () -> videoService.currentAuthorId(ADMIN_ID);
            var tasks = executor.invokeAll(java.util.List.of(resolve, resolve, resolve, resolve));
            var authorIds = new java.util.HashSet<Long>();
            for (var task : tasks) authorIds.add(task.get());
            org.junit.jupiter.api.Assertions.assertEquals(1, authorIds.size());
            org.junit.jupiter.api.Assertions.assertEquals(1,
                    jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE nickname = 'test-admin'", Integer.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Autowired
    private com.yingliu.service.content.video.VideoAdminService videoService;
    @Autowired
    private com.yingliu.service.content.video.mapper.VideoMapper videoMapper;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.yingliu.api.app.security.AppActorProvider appActor;

    @Test
    void appPublishesTextIntoReviewQueueAsCurrentUserAndRejectsEmptyContent() throws Exception {
        org.mockito.Mockito.when(appActor.currentUserId()).thenReturn(AUTHOR_ID);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/app/content")
                        .param("title", "用户纯文字").param("description", "<p><strong>正文</strong></p>")
                        .param("categoryIds", String.valueOf(CATEGORY_ID)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.auditStatus").value("PENDING_REVIEW"));
        org.junit.jupiter.api.Assertions.assertEquals(AUTHOR_ID,
                jdbcTemplate.queryForObject("SELECT author_id FROM video WHERE title = '用户纯文字'", Long.class));
        org.junit.jupiter.api.Assertions.assertNull(
                jdbcTemplate.queryForObject("SELECT original_media_id FROM video WHERE title = '用户纯文字'", Long.class));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM video_audit_record WHERE action = 'SUBMITTED'", Integer.class));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/app/content")
                        .param("title", "空内容").param("description", "<p><br>&nbsp;</p>")
                        .param("categoryIds", String.valueOf(CATEGORY_ID)))
                .andExpect(status().isUnprocessableEntity());
        jdbcTemplate.update("UPDATE sys_user SET status = 'MUTED' WHERE id = ?", AUTHOR_ID);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/app/content")
                        .param("title", "禁止发布").param("description", "正文").param("categoryIds", String.valueOf(CATEGORY_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesAndEditsImageOnlyContentWithoutRequiringVideo() throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/videos").contentType("application/json")
                        .content("{\"title\":\"纯图片\",\"description\":\"<p><img src='/media/images/a.png'></p>\",\"categoryIds\":[\"1005\"],\"tagIds\":[]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.originalMedia").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(response).path("data").path("id").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/videos/" + id)
                        .contentType("application/json").content("{\"title\":\"文字编辑\",\"description\":\"<p>更新正文</p>\",\"visibility\":\"PUBLIC\",\"categoryIds\":[\"1005\"],\"tagIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.description").value("<p>更新正文</p>"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/videos/" + id)
                        .contentType("application/json").content("{\"title\":\"空编辑\",\"description\":\"<p><br></p>\",\"visibility\":\"PUBLIC\",\"categoryIds\":[\"1005\"],\"tagIds\":[]}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM video_tag_map");
        jdbcTemplate.update("DELETE FROM video_category_map");
        jdbcTemplate.update("DELETE FROM video_publish_schedule");
        jdbcTemplate.update("DELETE FROM video_audit_record");
        jdbcTemplate.update("DELETE FROM video");
        jdbcTemplate.update("DELETE FROM content_tag");
        jdbcTemplate.update("DELETE FROM content_category");
        jdbcTemplate.update("DELETE FROM media_resource");
        jdbcTemplate.update("DELETE FROM sys_admin_user");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("INSERT INTO sys_user (id, nickname, status, deleted, created_at, updated_at) VALUES (?, ?, 'NORMAL', 0, NOW(3), NOW(3))", AUTHOR_ID, "测试作者");
        jdbcTemplate.update("INSERT INTO sys_admin_user (id, user_id, username, password_hash, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'NORMAL', NOW(3), NOW(3))", ADMIN_ID, AUTHOR_ID, "test-admin", "not-used");
        insertMedia(ORIGINAL_MEDIA_ID, "VIDEO_ORIGINAL", "READY", "original.mp4", "video/mp4", 1000L, 60000);
        insertMedia(COVER_MEDIA_ID, "COVER", "READY", "cover.jpg", "image/jpeg", 100L, null);
        jdbcTemplate.update("INSERT INTO content_category (id, name, sort, status, deleted, created_at, updated_at) VALUES (?, ?, 0, 'ENABLED', 0, NOW(3), NOW(3))", CATEGORY_ID, "测试分类");
        jdbcTemplate.update("INSERT INTO content_tag (id, name, status, heat_score, deleted, created_at, updated_at) VALUES (?, ?, 'ENABLED', 0, 0, NOW(3), NOW(3))", TAG_ID, "测试标签");
    }

    @Test
    void createsDraftAndSubmitsAdminVideoForReview() throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/videos")
                        .contentType("application/json")
                        .content("{\"authorId\":\"1002\",\"originalMediaId\":\"1003\",\"coverMediaId\":\"1004\",\"title\":\"测试视频\",\"categoryIds\":[\"1005\"],\"tagIds\":[\"1006\"],\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.auditStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.publishStatus").value("UNPUBLISHED"))
                .andExpect(jsonPath("$.data.originalMedia.relativePath").doesNotExist())
                .andExpect(jsonPath("$.data.categories[0].id").value("1005"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(response).path("data").path("id").asText();

        mockMvc.perform(get("/api/v1/admin/videos").param("keyword", "测试视频"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(id));

        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/submit-review", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("PENDING_REVIEW"));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_audit_record WHERE video_id = ? AND action = 'SUBMITTED'",
                Integer.class, Long.parseLong(id)));
    }

    @Test
    void uploadsReviewsAndPublishesVideoInOrder() throws Exception {
        String id = createDraft("完整发布流程");
        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/publish", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/submit-review", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("PENDING_REVIEW"));
        mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/claim", id)
                        .contentType("application/json").content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("CLAIMED"));
        mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/approve", id)
                        .contentType("application/json").content("{\"currentVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.publishStatus").value("UNPUBLISHED"));
        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/publish", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.publishStatus").value("PUBLISHED"));
    }

    @Test
    void reviewsPendingVideoAndKeepsAuditHistory() throws Exception {
        String id = createAndSubmit("审核视频");

        mockMvc.perform(get("/api/v1/admin/reviews/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(id))
                .andExpect(jsonPath("$.data.items[0].currentVersion").value(1));

        mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/approve", id)
                        .contentType("application/json")
                        .content("{\"currentVersion\":1,\"reason\":\"未认领直接审批\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/claim", id)
                        .contentType("application/json")
                        .content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("CLAIMED"));

        mockMvc.perform(get("/api/v1/admin/reviews/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(id));

        mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/approve", id)
                        .contentType("application/json")
                        .content("{\"currentVersion\":1,\"reason\":\"审核通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.publishStatus").value("UNPUBLISHED"));

        mockMvc.perform(get("/api/v1/admin/reviews/videos/{videoId}/history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].action").value("APPROVED"))
                .andExpect(jsonPath("$.data[1].action").value("CLAIMED"))
                .andExpect(jsonPath("$.data[2].action").value("SUBMITTED"));

        mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/reject", id)
                        .contentType("application/json")
                        .content("{\"currentVersion\":1,\"reason\":\"重复处理\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
    }

    @Test
    void resubmittedVideoReturnsToReviewManagementQueue() throws Exception {
        String id = createAndSubmit("重新审核视频");

        mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/claim", id)
                        .contentType("application/json")
                        .content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("CLAIMED"));
        mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/request-revision", id)
                        .contentType("application/json")
                        .content("{\"currentVersion\":1,\"reason\":\"请补充说明\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("REVISION_REQUIRED"));
        mockMvc.perform(get("/api/v1/admin/reviews/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/submit-review", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("PENDING_REVIEW"));
        mockMvc.perform(get("/api/v1/admin/reviews/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(id))
                .andExpect(jsonPath("$.data.items[0].currentVersion").value(1));
    }

    @Test
    void metadataUpdatesDoNotTransferReviewOwnership() throws Exception {
        String id = createAndSubmit("认领隔离视频");
        long videoId = Long.parseLong(id);
        videoService.claimReview(videoId, ADMIN_ID, 1);
        insertSecondAdmin();

        org.junit.jupiter.api.Assertions.assertEquals(1, videoMapper.updateMetadata(videoId, SECOND_ADMIN_ID, 1,
                "修改后的标题", null, "PUBLIC", ORIGINAL_MEDIA_ID));
        org.junit.jupiter.api.Assertions.assertEquals(ADMIN_ID,
                jdbcTemplate.queryForObject("SELECT review_claimed_by FROM video WHERE id = ?", Long.class, videoId));
        org.junit.jupiter.api.Assertions.assertThrows(com.yingliu.common.exception.BusinessException.class,
                () -> videoService.approveReview(videoId, SECOND_ADMIN_ID, 2, null));

        videoService.approveReview(videoId, ADMIN_ID, 2, null);
        org.junit.jupiter.api.Assertions.assertNull(
                jdbcTemplate.queryForObject("SELECT review_claimed_by FROM video WHERE id = ?", Long.class, videoId));
    }

    @Test
    void anotherReviewerCanReleaseAndReclaimAStuckReview() throws Exception {
        String id = createAndSubmit("释放认领视频");
        long videoId = Long.parseLong(id);
        insertSecondAdmin();
        videoService.claimReview(videoId, ADMIN_ID, 1);

        videoService.releaseReview(videoId, SECOND_ADMIN_ID, 1);
        org.junit.jupiter.api.Assertions.assertEquals("PENDING_REVIEW",
                jdbcTemplate.queryForObject("SELECT audit_status FROM video WHERE id = ?", String.class, videoId));
        org.junit.jupiter.api.Assertions.assertNull(
                jdbcTemplate.queryForObject("SELECT review_claimed_by FROM video WHERE id = ?", Long.class, videoId));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_audit_record WHERE video_id = ? AND reviewer_admin_id = ? AND action = 'RELEASED'",
                Integer.class, videoId, SECOND_ADMIN_ID));

        videoService.claimReview(videoId, SECOND_ADMIN_ID, 1);
        org.junit.jupiter.api.Assertions.assertEquals(SECOND_ADMIN_ID,
                jdbcTemplate.queryForObject("SELECT review_claimed_by FROM video WHERE id = ?", Long.class, videoId));
    }

    @Test
    void rejectsPendingVideoOnlyWithReason() throws Exception {
        String id = createAndSubmit("驳回视频");

        mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/claim", id)
                        .contentType("application/json")
                        .content("{\"version\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/reject", id)
                        .contentType("application/json")
                        .content("{\"currentVersion\":1,\"reason\":\" \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/reject", id)
                        .contentType("application/json")
                        .content("{\"currentVersion\":1,\"reason\":\"内容不符合规范\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("REJECTED"));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_audit_record WHERE video_id = ? AND action = 'REJECTED' AND reason = ?",
                Integer.class, Long.parseLong(id), "内容不符合规范"));
    }

    private String createAndApprove(String title) throws Exception {
        String id = createAndSubmit(title);
        mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/claim", id)
                        .contentType("application/json")
                        .content("{\"version\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/approve", id)
                        .contentType("application/json")
                        .content("{\"currentVersion\":1}"))
                .andExpect(status().isOk());
        return id;
    }

    private String createAndSubmit(String title) throws Exception {
        String id = createDraft(title);
        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/submit-review", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("PENDING_REVIEW"));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_audit_record WHERE video_id = ? AND action = 'SUBMITTED'",
                Integer.class, Long.parseLong(id)));
        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/submit-review", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_audit_record WHERE video_id = ? AND action = 'SUBMITTED'",
                Integer.class, Long.parseLong(id)));
        return id;
    }

    private String createDraft(String title) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/videos")
                        .contentType("application/json")
                        .content("{\"authorId\":\"1002\",\"originalMediaId\":\"1003\",\"coverMediaId\":\"1004\",\"title\":\"" + title + "\",\"categoryIds\":[\"1005\"],\"tagIds\":[],\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.auditStatus").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asText();
    }

    private void insertSecondAdmin() {
        jdbcTemplate.update("INSERT INTO sys_admin_user (id, user_id, username, password_hash, status, created_at, updated_at) VALUES (?, NULL, ?, ?, 'NORMAL', NOW(3), NOW(3))",
                SECOND_ADMIN_ID, "backup-reviewer", "not-used");
    }

    @Test
    void offShelvesRestoresAndDeletesPublishedVideo() throws Exception {
        String id = createAndApprove("处置视频");
        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/publish", id)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/off-shelf", id)
                        .contentType("application/json")
                        .content("{\"reason\":\"违规内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishStatus").value("OFF_SHELF"));
        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/off-shelf", id)
                        .contentType("application/json")
                        .content("{\"reason\":\"重复下架\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/restore", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.publishStatus").value("UNPUBLISHED"));
        mockMvc.perform(get("/api/v1/admin/reviews/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(id));
        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/publish", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/claim", id)
                        .contentType("application/json")
                        .content("{\"version\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/approve", id)
                        .contentType("application/json")
                        .content("{\"currentVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("APPROVED"));
        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/publish", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishStatus").value("PUBLISHED"));

        mockMvc.perform(delete("/api/v1/admin/videos/{videoId}", id)
                        .contentType("application/json")
                        .content("{\"reason\":\"内容删除\"}"))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT deleted FROM video WHERE id = ?", Integer.class, Long.parseLong(id)));
        mockMvc.perform(get("/api/v1/admin/videos/{videoId}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VIDEO_NOT_FOUND"));
    }

    @Test
    void batchRestoreSubmitsOffShelfVideosForReview() throws Exception {
        String id = createAndApprove("批量重审视频");
        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/publish", id)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/off-shelf", id)
                        .contentType("application/json").content("{\"reason\":\"批量测试\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/videos/batch/restore")
                        .contentType("application/json").content("{\"videoIds\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeeded[0]").value(Long.parseLong(id)));
        mockMvc.perform(get("/api/v1/admin/videos/{videoId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.publishStatus").value("UNPUBLISHED"));

        mockMvc.perform(post("/api/v1/admin/videos/batch/restore")
                        .contentType("application/json").content("{\"videoIds\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeeded[0]").value(Long.parseLong(id)));
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_audit_record WHERE video_id = ? AND action = 'SUBMITTED'",
                Integer.class, Long.parseLong(id)));
    }

    @Test
    void publishesAndSchedulesApprovedVideos() throws Exception {
        String publishId = createAndApprove("立即发布视频");
        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/publish", publishId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.publishStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.scheduledAt").doesNotExist());
        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/publish", publishId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        String scheduleId = createAndApprove("定时发布视频");
        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/schedule-publish", scheduleId)
                        .contentType("application/json")
                        .content("{\"scheduledAt\":\"2099-01-01T00:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.scheduledAt").isNotEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_publish_schedule WHERE video_id = ? AND status = 'PENDING'",
                Integer.class, Long.parseLong(scheduleId)));

        mockMvc.perform(post("/api/v1/admin/videos/{videoId}/cancel-scheduled-publish", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishStatus").value("UNPUBLISHED"))
                .andExpect(jsonPath("$.data.scheduledAt").doesNotExist());
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_publish_schedule WHERE video_id = ? AND status = 'CANCELLED'",
                Integer.class, Long.parseLong(scheduleId)));
    }

    @Test
    void rejectsMediaThatIsNotReady() throws Exception {
        jdbcTemplate.update("UPDATE media_resource SET storage_state = 'VERIFYING' WHERE id = ?", ORIGINAL_MEDIA_ID);
        mockMvc.perform(post("/api/v1/admin/videos")
                        .contentType("application/json")
                        .content("{\"authorId\":\"1002\",\"originalMediaId\":\"1003\",\"title\":\"测试视频\",\"categoryIds\":[\"1005\"],\"tagIds\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_READY"));
    }

    private void insertMedia(long id, String resourceType, String state, String path, String contentType, long size, Integer duration) {
        jdbcTemplate.update("INSERT INTO media_resource (id, resource_type, storage_state, relative_path, content_type, file_size, sha256, duration_ms, backup_status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', NOW(3), NOW(3))",
                id, resourceType, state, path, contentType, size, String.format("%064d", id), duration);
    }

    @TestConfiguration
    static class ActorConfiguration {
        @Bean
        @Primary
        AdminActorProvider adminActorProvider() {
            return () -> ADMIN_ID;
        }
    }
}
