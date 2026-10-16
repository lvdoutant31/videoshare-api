# Watch Time Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure a real C-end video playback produces watch-time records and that B-end and C-end read the same per-video watch-time aggregate.

**Architecture:** Keep `video_watch_event`, `video_watch_session`, and `video_watch_daily` as the single source of truth. Enable the C-end analytics transport for normal builds, expose `watchTimeMs` from the existing daily aggregate in both app and admin video APIs, and keep the current 3-second effective-play and idempotent event semantics.

**Tech Stack:** Vue 3, TypeScript, Vitest, Java 17, Spring JDBC/MyBatis, Spring Boot, JUnit.

## Global Constraints

- Preserve unrelated uncommitted work in all three repositories.
- Do not introduce a second watch-time counter or derive watch time from `video_stats.play_count`.
- `watchTimeMs` must mean the sum of `video_watch_daily.watch_time_ms` for one video, in milliseconds.
- Duplicate `eventId` values must remain idempotent.
- Existing API fields remain backward-compatible; new watch-time fields are additive.

---

### Task 1: Enable and verify C-end progress reporting

**Files:**
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-c/.env`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-c/src/analytics/index.ts`
- Test: `D:/PrivateSoftware/gitClaude/yingliu-c/src/components/FeedPlayer.spec.ts`

**Interfaces:**
- Consumes: `FeedPlayer` progress events with `watchedDeltaMs`, `positionMs`, and `durationMs`.
- Produces: real builds queue and POST `video_play_progress` events to `/api/v1/analytics/events/batch`.

- [x] **Step 1: Write the failing test**

Add a test that verifies the analytics module does not silently disable `track` in the normal local environment and that `flush()` posts a queued progress event.

- [x] **Step 2: Run the focused test and verify failure**

Run: `npm test -- --run src/components/FeedPlayer.spec.ts src/analytics/index.spec.ts`

Expected: the new transport assertion fails while the local `.env` explicitly sets `VITE_ANALYTICS_ENABLED=false` or the flush behavior is not covered.

- [x] **Step 3: Implement the minimal fix**

Set normal `.env` analytics to true, preserve Playwright's explicit disabled override, and make the analytics transport retain failed batches without losing the progress event needed for the next retry.

- [x] **Step 4: Run the focused tests**

Run: `npm test -- --run src/components/FeedPlayer.spec.ts src/analytics/index.spec.ts`

Expected: all focused tests pass and the player heartbeat still emits a 5-second wall-clock delta.

---

### Task 2: Expose one canonical watch-time field to B and C

**Files:**
- Modify: `D:/PrivateSoftware/gitClaude/ticktok-api/yingliu-service/src/main/java/com/yingliu/service/content/video/mapper/VideoMapper.java`
- Modify: `D:/PrivateSoftware/gitClaude/ticktok-api/yingliu-service/src/main/java/com/yingliu/service/content/video/VideoAppService.java`
- Modify: `D:/PrivateSoftware/gitClaude/ticktok-api/yingliu-service/src/main/java/com/yingliu/service/content/video/DefaultVideoAppService.java`
- Modify: `D:/PrivateSoftware/gitClaude/ticktok-api/yingliu-api-app/src/main/java/com/yingliu/api/app/content/AppFeedAssembler.java`
- Modify: `D:/PrivateSoftware/gitClaude/ticktok-api/yingliu-api-app/src/main/java/com/yingliu/api/app/content/video/AppVideoDtos.java`
- Modify: `D:/PrivateSoftware/gitClaude/ticktok-api/yingliu-service/src/main/java/com/yingliu/service/content/video/VideoAdminService.java`
- Modify: `D:/PrivateSoftware/gitClaude/ticktok-api/yingliu-service/src/main/java/com/yingliu/service/content/video/DefaultVideoAdminService.java`
- Modify: `D:/PrivateSoftware/gitClaude/ticktok-api/yingliu-api-admin/src/main/java/com/yingliu/api/admin/content/video/AdminVideoController.java`
- Modify: `D:/PrivateSoftware/gitClaude/ticktok-api/yingliu-api-admin/src/main/java/com/yingliu/api/admin/content/video/VideoDtos.java`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/features/video/model.ts`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/features/video/repository.ts`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/views/VideoManagementView.vue`

**Interfaces:**
- Produces app `Stats.watchTimeMs` and admin `VideoSummary.watchTimeMs`/`VideoDetail.watchTimeMs`.
- All values come from `SUM(video_watch_daily.watch_time_ms)` grouped by `video_id`.

- [x] **Step 1: Add failing API/service tests**

Assert that app video stats and admin video summaries expose `watchTimeMs` from the daily aggregate.

- [x] **Step 2: Implement one aggregate query and map it in both services**

Use one batched `video_id IN (...)` query for list responses and one ID query for details; missing rows map to zero.

- [x] **Step 3: Render the B-end watch-time field**

Display the canonical milliseconds converted to a readable duration while retaining existing play and interaction counts.

- [x] **Step 4: Run backend and frontend focused tests**

Run service tests, admin package compilation, and B/C type checks/tests.

---

### Task 3: Cross-layer verification

**Files:** Verify only the files above.

- [x] **Step 1: Run the playback integration test**

Run the existing `watchProgressIsIdempotentAndCountsActualWallClockTime` integration test and confirm both session and daily `watch_time_ms` increase by the accepted deltas only once.

- [x] **Step 2: Build all clients and the admin server**

Run C build, B typecheck/build, service tests, and `yingliu-admin` package.

- [x] **Step 3: Review scoped diffs**

Run `git diff --check` only on the changed watch-time files and preserve unrelated dirty changes.
