# Comment Video Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show each managed comment's source video in the B-side UI and soft-delete all comments when their video is soft-deleted.

**Architecture:** Extend the active moderation query contract (`ModerationService` → `JdbcModerationService` → `ModerationController`) with video title and author data already required by the B-side model. Keep deletion transactional by adding one mapper update for `video_comment` and invoking it from both single and batch video deletion paths after the video update succeeds.

**Tech Stack:** Java 17, Spring Boot, Spring JDBC, MyBatis annotations, JUnit 5, Mockito, Vue 3, TypeScript, Vitest.

## Global Constraints

- Video and comment deletion remain soft deletion; no physical rows are removed.
- A deleted child comment uses `status='DELETED'`, `deleted_at`, `deleted_by`, `delete_reason`, and `updated_at`.
- Single and batch video deletion must apply the same comment cascade inside the existing transaction.
- Preserve unrelated working-tree changes in both repositories.

---

### Task 1: Align the active moderation comment contract with the B-side model

**Files:**
- Modify: `yingliu-service/src/main/java/com/yingliu/service/moderation/ModerationService.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/moderation/JdbcModerationService.java`
- Modify: `yingliu-api-admin/src/main/java/com/yingliu/api/admin/moderation/ModerationDtos.java`
- Modify: `yingliu-api-admin/src/main/java/com/yingliu/api/admin/moderation/ModerationController.java`
- Create: `yingliu-service/src/test/java/com/yingliu/service/moderation/JdbcModerationServiceTest.java`

**Interfaces:**
- Consumes: `video_comment.video_id`, `video.title`, and `sys_user.nickname`.
- Produces: comment JSON fields `videoTitle` and `author`, matching `ManagedComment.videoTitle` and `ManagedComment.author` in `yingliu-b`.

- [ ] **Step 1: Write a failing JDBC repository test**

Create a Mockito-backed `JdbcModerationServiceTest` that calls `comments(1, 20, null, null, null)`, captures the list SQL, asserts it joins `video` and `sys_user`, maps a mocked row containing `videoTitle="测试视频"` and `author="测试用户"`, and asserts those values on the returned comment.

```java
assertThat(sql).contains("JOIN video v ON v.id=c.video_id");
assertThat(sql).contains("JOIN sys_user u ON u.id=c.user_id");
assertThat(page.items().getFirst().videoTitle()).isEqualTo("测试视频");
assertThat(page.items().getFirst().author()).isEqualTo("测试用户");
```

- [ ] **Step 2: Run the focused test and verify the contract is currently absent**

Run:

```powershell
.\mvnw.cmd -pl yingliu-service -am '-Dtest=JdbcModerationServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: FAIL because `ModerationService.Comment` has no `videoTitle()` or `author()` accessors and the SQL does not join the related tables.

- [ ] **Step 3: Extend the service record and JDBC query**

Add `String videoTitle` and `String author` to `ModerationService.Comment`. Qualify the existing filters with alias `c`, and use the same row mapper for list and detail queries:

```java
SELECT c.id,c.video_id,v.title,c.user_id,u.nickname,c.content,c.status,c.risk_level,
       c.like_count,c.reply_count,c.created_at,c.updated_at
FROM video_comment c
JOIN video v ON v.id=c.video_id
JOIN sys_user u ON u.id=c.user_id
```

- [ ] **Step 4: Expose the fields in the API DTO**

Extend `ModerationDtos.Comment` with `videoTitle` and `author`, then update `ModerationController.c(...)` to map both values:

```java
new ModerationDtos.Comment("" + x.id(), "" + x.videoId(), x.videoTitle(),
        "" + x.userId(), x.author(), x.content(), x.status(), x.riskLevel(),
        x.likeCount(), x.replyCount(), t(x.createdAt()), t(x.updatedAt()))
```

- [ ] **Step 5: Run the focused service test and compile the admin API**

Run:

```powershell
.\mvnw.cmd -pl yingliu-api-admin -am '-Dtest=JdbcModerationServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: PASS and `BUILD SUCCESS`.

### Task 2: Cascade video soft deletion to its comments

**Files:**
- Modify: `yingliu-service/src/main/java/com/yingliu/service/content/video/mapper/VideoMapper.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/content/video/DefaultVideoAdminService.java`
- Create: `yingliu-service/src/test/java/com/yingliu/service/content/video/VideoDeletionTest.java`

**Interfaces:**
- Consumes: existing `VideoMapper.deleteVideo(long videoId, long actorAdminId)` success result and the validated deletion reason.
- Produces: `VideoMapper.deleteCommentsByVideoId(long videoId, long actorAdminId, String reason): int`.

- [ ] **Step 1: Write failing tests for single and batch deletion**

Construct `DefaultVideoAdminService` with mocked `VideoMapper`, `AdminUserMapper`, and `VideoPublishScheduleMapper`. Stub a normal admin, an existing video, and successful `deleteVideo`, then verify both paths call:

```java
verify(videoMapper).deleteCommentsByVideoId(11L, 7L, "删除视频");
```

For batch deletion, also assert video `11L` appears in `succeededIds()`.

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
.\mvnw.cmd -pl yingliu-service -am '-Dtest=VideoDeletionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: FAIL because `deleteCommentsByVideoId` does not exist and neither deletion path invokes it.

- [ ] **Step 3: Add the soft-delete mapper operation**

Add this annotated mapper method:

```java
@Update("UPDATE video_comment SET status='DELETED', deleted_at=NOW(3), deleted_by=#{actorAdminId}, "
        + "delete_reason=#{reason}, updated_at=NOW(3) WHERE video_id=#{videoId} AND status<>'DELETED'")
int deleteCommentsByVideoId(@Param("videoId") long videoId,
                            @Param("actorAdminId") long actorAdminId,
                            @Param("reason") String reason);
```

- [ ] **Step 4: Invoke the cascade from both transactional delete paths**

In `batchDelete`, call the new mapper method after `deleteVideo` with `normalizedReason`. In `delete`, retain the result of `requireText(...)` and pass it to the mapper. Keep `publishScheduleMapper.cancelActiveByVideoId(...)` unchanged.

- [ ] **Step 5: Run deletion tests**

Run:

```powershell
.\mvnw.cmd -pl yingliu-service -am '-Dtest=VideoDeletionTest,VideoMapperReviewFlowTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: all focused tests PASS.

### Task 3: Lock the B-side video-title rendering with a regression test

**Files:**
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/views/CommentReportManagementView.spec.ts`
- Verify only: `D:/PrivateSoftware/gitClaude/yingliu-b/src/views/CommentReportManagementView.vue`

**Interfaces:**
- Consumes: backend comment field `videoTitle` through `ManagedComment`.
- Produces: visible source-video context in the comment table and detail drawer using the existing `{{ row.videoTitle }}` and `{{ selectedComment.videoTitle }}` bindings.

- [ ] **Step 1: Strengthen the existing view test**

Add this assertion to `loads the comment management queue`:

```ts
expect(wrapper.text()).toContain('车间安全巡检规范');
```

- [ ] **Step 2: Run the B-side tests and typecheck**

Run:

```powershell
npm test -- src/features/comment-report/repository.spec.ts src/views/CommentReportManagementView.spec.ts
npm run typecheck
```

Expected: 7 repository/view tests plus the updated assertion PASS, and typecheck exits successfully.

- [ ] **Step 3: Run the complete backend verification**

Run:

```powershell
.\mvnw.cmd -pl yingliu-admin -am test
```

Expected: reactor `BUILD SUCCESS`; video delete and comment-management tests remain green.

### Task 4: Final cross-repository review

**Files:**
- Review: all files changed in Tasks 1–3.

**Interfaces:**
- Consumes: backend JSON contract and B-side `ManagedComment` model.
- Produces: one consistent end-to-end behavior with no unrelated edits.

- [ ] **Step 1: Inspect scoped diffs**

Run `git diff --` for the listed backend files and `git -C D:/PrivateSoftware/gitClaude/yingliu-b diff --` for the comment-report files. Confirm unrelated video-review changes remain untouched.

- [ ] **Step 2: Confirm requirement coverage**

Verify the final diff satisfies both conditions: every managed comment carries and renders its video title; every successful single or batch video soft delete marks all comments for that video `DELETED` with actor, reason, and timestamps.

