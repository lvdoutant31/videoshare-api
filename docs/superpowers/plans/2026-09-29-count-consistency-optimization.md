# Interaction Count Consistency Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make video likes, video favorites, and comment likes idempotent under duplicate or concurrent requests while returning authoritative counts with fewer database round trips.

**Architecture:** Keep the relationship tables as the source of truth and retain the existing denormalized counters. Replace read-before-write checks with mapper mutations whose affected-row count decides whether counters change, and keep relationship and counter changes in the existing Spring transaction. Do not add Redis, queues, schema changes, or asynchronous consistency in this phase.

**Tech Stack:** Java 17, Spring Boot 3.3, MyBatis-Plus, MySQL 8, JUnit 5, Mockito

## Global Constraints

- Preserve the existing `PUT` endpoints and request/response shapes.
- Keep relationship and counter mutations in one `@Transactional` operation.
- Do not change feed read paths or introduce new infrastructure.
- Preserve all unrelated dirty-worktree changes.
- Verify duplicate activation and duplicate deactivation do not alter counters.

---

### Task 1: Video Like And Favorite Conditional Mutations

**Files:**
- Create: `yingliu-service/src/test/java/com/yingliu/service/app/DefaultAppInteractionServiceTest.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/app/mapper/UserVideoLikeMapper.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/app/mapper/UserVideoFavoriteMapper.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/app/DefaultAppInteractionService.java`

**Interfaces:**
- Produces: `UserVideoLikeMapper.insertIfAbsent(long userId, long videoId)` and `deleteByUserAndVideo(long userId, long videoId)` returning affected rows.
- Produces: equivalent methods on `UserVideoFavoriteMapper`.
- Preserves: `AppInteractionService.TargetState setVideoLike(...)` and `setVideoFavorite(...)`.

- [x] **Step 1: Write failing service tests**

Add Mockito tests proving an insert result of `0` and a delete result of `0` never call the counter mappers, while a result of `1` updates the video count and the author's received-like count exactly once.

- [x] **Step 2: Run the focused tests and confirm failure**

Run: `.\mvnw.cmd -pl yingliu-service -am "-Dtest=DefaultAppInteractionServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: compilation fails because the conditional mapper methods do not exist yet.

- [x] **Step 3: Add conditional mapper SQL**

Use `INSERT IGNORE` for activation and an exact-key `DELETE` for deactivation. Return MyBatis affected-row counts so duplicate requests produce `0` and first mutations produce `1`.

- [x] **Step 4: Replace read-before-write service logic**

Remove the preliminary `selectCount` calls and the exception-swallowing helper methods. Adjust `video_stats` and `sys_user.like_received_count` only when the relationship mapper returns `1`; continue reading the final denormalized count for the response.

- [x] **Step 5: Run focused tests**

Run: `.\mvnw.cmd -pl yingliu-service -am "-Dtest=DefaultAppInteractionServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: all new interaction-service tests pass.

### Task 2: Comment Like Conditional Mutation And Accurate Response

**Files:**
- Create: `yingliu-service/src/test/java/com/yingliu/service/app/DefaultAppCommentServiceTest.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/app/mapper/UserCommentLikeMapper.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/app/mapper/VideoCommentMapper.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/app/DefaultAppCommentService.java`

**Interfaces:**
- Produces: `UserCommentLikeMapper.insertIfAbsent(long userId, long commentId)` and `deleteByUserAndComment(long userId, long commentId)` returning affected rows.
- Produces: `VideoCommentMapper.adjustLikeCountAndHotScore(long commentId, int delta, int bonus)`.
- Produces: `VideoCommentMapper.selectLikeCount(long commentId)` returning the authoritative stored count.

- [x] **Step 1: Write failing comment-like tests**

Add Mockito tests covering first like, duplicate like, first unlike, and duplicate unlike. Assert that count/hot-score adjustment occurs only for affected-row result `1`, and that unlike returns the mapper's post-mutation count rather than the pre-mutation entity value.

- [x] **Step 2: Run the focused tests and confirm failure**

Run: `.\mvnw.cmd -pl yingliu-service -am "-Dtest=DefaultAppCommentServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: compilation fails because the new mapper methods do not exist yet.

- [x] **Step 3: Add mapper operations**

Add `INSERT IGNORE`, exact-key `DELETE`, one combined atomic update for `like_count` and `hot_score`, and a scalar count query by primary key.

- [x] **Step 4: Update the comment service**

Remove `selectCount` and duplicate-key exception handling. Apply the combined counter update only when the relationship changes, then return `selectLikeCount(commentId)` clamped to zero.

- [x] **Step 5: Run focused tests**

Run: `.\mvnw.cmd -pl yingliu-service -am "-Dtest=DefaultAppCommentServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: all new comment-service tests pass.

### Task 3: API Regression And Concurrency Verification

**Files:**
- Modify: `yingliu-admin/src/test/java/com/yingliu/api/app/AppApiIntegrationTest.java`

**Interfaces:**
- Consumes: the unchanged like, favorite, and comment-like HTTP endpoints.
- Verifies: relationship-row count and denormalized counters remain equal after duplicate concurrent requests.

- [x] **Step 1: Extend sequential API assertions**

Extend the existing comment flow to assert duplicate like stays at `1`, first unlike returns `0`, and duplicate unlike stays at `0`.

- [x] **Step 2: Add a simultaneous duplicate-like regression**

Use two executor tasks released by a `CountDownLatch` to submit the same video-like activation. Assert both responses succeed and the database ends with one relationship row, `video_stats.like_count = 1`, and `sys_user.like_received_count = 1`.

- [x] **Step 3: Run service tests**

Run: `.\mvnw.cmd -pl yingliu-service -am test`

Expected: `BUILD SUCCESS`.

- [x] **Step 4: Run the focused API integration test**

Run: `.\mvnw.cmd -pl yingliu-admin -am "-Dtest=AppApiIntegrationTest#concurrentDuplicateLikesIncrementCountersOnce+commentLikeIsIdempotentAndUnlikeReturnsNewCount" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: tests pass when MySQL/Testcontainers is available; otherwise the existing `@EnabledIf` condition reports the class skipped.

- [x] **Step 5: Build the backend artifact**

Run: `.\mvnw.cmd -B -pl yingliu-admin -am package -DskipTests`

Expected: all reactor modules report `SUCCESS`.

## Self-Review

- Spec coverage: video like, video favorite, and comment like idempotency plus response-count correctness are covered.
- Placeholder scan: the plan contains no deferred implementation placeholders.
- Type consistency: all mapper mutations return `int`; public service and HTTP response signatures stay unchanged.
- Scope: comment visibility/counting semantics, Redis counters, outbox processing, and reconciliation jobs remain separate future phases.
