# Admin Dashboard Data Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace placeholder dashboard metrics with useful live operational fields and expose actionable navigation and recent-video interaction data to the admin frontend.

**Architecture:** Keep the existing four dashboard endpoints and the frontend snapshot composition. Compute summary and trend values directly from existing transactional tables because `daily_metric` currently has no producer; extend the existing records and TypeScript models without adding migrations or background jobs.

**Tech Stack:** Java 17, Spring JDBC, Spring Boot, Vue 3, TypeScript, Element Plus, Vitest.

## Global Constraints

- Preserve unrelated uncommitted work in both repositories.
- Do not add a database migration or a scheduled aggregation job.
- Keep `/api/v1/admin/dashboard/summary`, `/trends`, `/todos`, and `/storage` backward-compatible by only adding fields or replacing metric items inside the existing arrays.
- Dashboard metrics must come from existing business tables, not mock constants.

---

### Task 1: Backend dashboard contract and live queries

**Files:**
- Modify: `yingliu-service/src/main/java/com/yingliu/service/dashboard/DashboardService.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/dashboard/JdbcDashboardService.java`
- Modify: `yingliu-api-admin/src/main/java/com/yingliu/api/admin/dashboard/DashboardDtos.java`
- Create: `yingliu-service/src/test/java/com/yingliu/service/dashboard/JdbcDashboardServiceTest.java`

**Interfaces:**
- Produces: `Todo(String key, String label, long count, String tone, String to)`.
- Produces: `RecentVideo(String id, String title, String author, String status, Long playCount, Long interactionCount, LocalDateTime publishedAt, LocalDateTime updatedAt)`.
- Produces metric keys `plays`, `newUsers`, `publishedVideos`, and `interactions`.

- [x] **Step 1: Write focused service tests**

Create Mockito tests that stub the summary aggregate row and verify metric labels/values/change labels, todo routes, and the extended recent-video fields.

- [x] **Step 2: Run the focused test and verify failure**

Run: `.\\mvnw.cmd -pl yingliu-service -am "-Dtest=JdbcDashboardServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: compilation failure until the new record fields and query mapping exist.

- [x] **Step 3: Implement live summary and trend queries**

Use indexed date ranges over `video_watch_daily`, `sys_user`, `video`, `user_video_like`, `user_video_favorite`, and `video_comment`. Generate missing dates in the 7/30-day trend with a MySQL 8 recursive CTE so the chart does not contain gaps.

- [x] **Step 4: Add actionable todo routes and recent-video interaction fields**

Map `/reviews`, `/reports`, and `/videos` in todo records. Extend recent videos with `interactionCount` and `updatedAt`, and map both fields through `DashboardDtos`.

- [x] **Step 5: Run backend tests**

Run: `.\\mvnw.cmd -pl yingliu-service -am test`

Expected: all service and dependency tests pass.

### Task 2: Admin frontend field model and presentation

**Files:**
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/features/dashboard/model.ts`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/features/dashboard/mockRepository.ts`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/features/dashboard/components/RecentVideosTable.vue`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/features/dashboard/repository.spec.ts`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/features/dashboard/components/DashboardComponents.spec.ts`

**Interfaces:**
- Consumes: the extended dashboard summary DTO from Task 1.
- Produces: a six-column recent-video table showing effective plays, interactions, and latest update time.

- [x] **Step 1: Update failing frontend expectations**

Assert the snapshot contains `publishedVideos` and `interactions`, and mount the recent-video table to verify interaction and update-time rendering.

- [x] **Step 2: Run focused Vitest tests and verify failure**

Run: `npm test -- src/features/dashboard/repository.spec.ts src/features/dashboard/components/DashboardComponents.spec.ts`

Expected: type or assertion failure before the model and component changes.

- [x] **Step 3: Update models, mock data, and table columns**

Replace unsupported metric keys with `publishedVideos` and `interactions`; add `interactionCount` and `updatedAt` to `RecentVideo`; render interaction count and the most recent timestamp.

- [x] **Step 4: Verify the frontend**

Run: `npm test -- src/features/dashboard/repository.spec.ts src/features/dashboard/components/DashboardComponents.spec.ts`

Run: `npm run typecheck`

Run: `npm run build`

Expected: all commands succeed.

### Task 3: Cross-layer verification

**Files:**
- Verify only: all files from Tasks 1 and 2.

- [x] **Step 1: Package the backend**

Run: `.\\mvnw.cmd -B -pl yingliu-admin -am package -DskipTests`

Expected: all reactor modules succeed and the Spring Boot JAR is produced.

- [x] **Step 2: Review the scoped diff**

Run `git diff --check` against only the dashboard files in each repository and confirm no unrelated file was modified by this implementation.
