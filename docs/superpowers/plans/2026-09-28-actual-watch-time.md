# Actual Watch Time Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Measure real wall-clock video watch time, aggregate it idempotently, and expose the resulting metrics on the discovery dashboard.

**Architecture:** The Vue player emits five-second watch heartbeats containing a unique event ID, playback-session ID, wall-clock delta, and media position. The existing analytics endpoint stores the raw event and synchronously applies each unique heartbeat to session, daily, and cumulative statistics so the current deployment needs no new worker process. The discovery API reads those aggregates and the frontend renders actual watch-time metrics.

**Tech Stack:** Vue 3, TypeScript, Vitest, Spring Boot, JdbcTemplate, MyBatis annotations, Flyway, MySQL 8, JUnit/Testcontainers.

## Global Constraints

- Actual watch time is wall-clock time while media is playing, not seeking, not buffering, and visible.
- A two-times-speed video watched for 10 seconds contributes 10 seconds of watch time and about 20 seconds of media progress.
- Heartbeats are emitted every 5 seconds and the backend caps a single delta at 15 seconds.
- `eventId` makes retries idempotent; `playSessionId` groups one continuous video playback.
- Effective play means a session reaches 3 seconds of accumulated watch time; completion means media progress reaches 90 percent.
- Preserve unrelated dirty-worktree changes and do not create commits during inline execution.
- Existing discovery search parameters and the black/blue/white/gray visual system remain unchanged.

---

### Task 1: Persist and aggregate watch heartbeats

**Files:**
- Create: `yingliu-admin/src/main/resources/db/migration/V202609281200__add_video_watch_metrics.sql`
- Create: `yingliu-service/src/main/java/com/yingliu/service/app/VideoWatchMetricsService.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/app/AppAnalyticsService.java`
- Modify: `yingliu-api-app/src/main/java/com/yingliu/api/app/analytics/AppAnalyticsController.java`
- Test: `yingliu-admin/src/test/java/com/yingliu/api/app/AppApiIntegrationTest.java`

**Interfaces:**
- Consumes analytics events with `eventId`, `playSessionId`, `videoId`, `watchedDeltaMs`, `positionMs`, and `durationMs`.
- Produces idempotent rows in `video_watch_event`, `video_watch_session`, and `video_watch_daily`, plus `video_stats.play_count` updates.

- [ ] **Step 1: Add a failing integration test**

Post the same heartbeat twice and assert one event, 5,000 ms of session/daily watch time, and one effective play only after a later heartbeat crosses 3,000 ms:

```java
mockMvc.perform(post("/api/v1/analytics/events/batch")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"events\":[{\"eventId\":\"...\",\"playSessionId\":\"...\","
                + "\"eventName\":\"video_play_progress\",\"videoId\":\"2003\","
                + "\"anonymousSessionId\":\"...\",\"properties\":{\"watchedDeltaMs\":5000,"
                + "\"positionMs\":5000,\"durationMs\":60000}}]}"))
        .andExpect(status().isOk());
```

- [ ] **Step 2: Run the focused integration test and confirm it fails**

Run: `mvn -pl yingliu-admin -am -Dtest=AppApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Add the Flyway schema**

Create stable tables with these keys:

```sql
CREATE TABLE video_watch_event (event_id CHAR(36) PRIMARY KEY, play_session_id CHAR(36) NOT NULL, video_id BIGINT UNSIGNED NOT NULL, watched_delta_ms INT UNSIGNED NOT NULL, occurred_at DATETIME(3) NOT NULL);
CREATE TABLE video_watch_session (play_session_id CHAR(36) PRIMARY KEY, video_id BIGINT UNSIGNED NOT NULL, watch_time_ms BIGINT UNSIGNED NOT NULL DEFAULT 0, max_position_ms INT UNSIGNED NOT NULL DEFAULT 0, duration_ms INT UNSIGNED NULL, effective_play TINYINT(1) NOT NULL DEFAULT 0, completed TINYINT(1) NOT NULL DEFAULT 0, started_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL);
CREATE TABLE video_watch_daily (metric_date DATE NOT NULL, video_id BIGINT UNSIGNED NOT NULL, effective_play_count BIGINT UNSIGNED NOT NULL DEFAULT 0, watch_time_ms BIGINT UNSIGNED NOT NULL DEFAULT 0, complete_count BIGINT UNSIGNED NOT NULL DEFAULT 0, PRIMARY KEY(metric_date, video_id));
```

- [ ] **Step 4: Implement idempotent aggregation**

Expose:

```java
public void record(String eventId, String playSessionId, long videoId,
                   int watchedDeltaMs, int positionMs, Integer durationMs,
                   LocalDateTime occurredAt)
```

Insert `video_watch_event` first with `INSERT IGNORE`; return when it already exists. Lock/create the session, cap the delta to 15,000 ms, detect the effective/completion transitions, update daily aggregates, and increment `video_stats.play_count` only on the effective transition.

- [ ] **Step 5: Extend the analytics contract**

Add `eventId` and `playSessionId` to `EventBody` and `EventInput`; route `video_play_progress` events with valid IDs to `VideoWatchMetricsService` after raw-event validation.

- [ ] **Step 6: Run the integration test and confirm it passes**

Run the command from Step 2 and verify duplicate delivery does not change totals.

---

### Task 2: Return actual watch-time discovery metrics

**Files:**
- Modify: `yingliu-service/src/main/java/com/yingliu/service/content/video/DiscoverQueryRows.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/content/video/mapper/VideoMapper.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/content/video/VideoAppService.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/content/video/DefaultVideoAppService.java`
- Modify: `yingliu-api-app/src/main/java/com/yingliu/api/app/content/video/AppVideoDtos.java`
- Modify: `yingliu-api-app/src/main/java/com/yingliu/api/app/content/AppFeedAssembler.java`
- Test: `yingliu-admin/src/test/java/com/yingliu/api/app/AppVideoIntegrationTest.java`

**Interfaces:**
- Produces `summary.totalWatchTimeMs`, `summary.effectivePlayCount`, `summary.averageWatchTimeMs`, and watch-session buckets in `durationStats`.
- Existing `categories`, `tags`, `ranking`, and `videos` remain compatible.

- [ ] **Step 1: Extend the discovery integration test**

Seed watch sessions/daily aggregates and assert:

```java
jsonPath("$.data.summary.totalWatchTimeMs").value(45000)
jsonPath("$.data.summary.effectivePlayCount").value(2)
jsonPath("$.data.summary.averageWatchTimeMs").value(22500)
jsonPath("$.data.durationStats[0].label").value("10秒以内")
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run: `mvn -pl yingliu-admin -am -Dtest=AppVideoIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Replace media-duration aggregation with watch aggregation**

Query public-video sessions into the bands `<10s`, `10–30s`, `30–60s`, `1–3m`, and `>=3m`; sum `video_watch_daily.watch_time_ms` and effective plays for summary values.

- [ ] **Step 4: Use recent effective plays in ranking**

Join the most recent seven days of `video_watch_daily` and include effective plays in the existing interaction score without changing filtered-search semantics.

- [ ] **Step 5: Update service and DTO records**

Use:

```java
record Summary(long totalVideos, long totalWatchTimeMs, long effectivePlayCount,
               long averageWatchTimeMs, long todayNewCount) {}
record DurationStat(String code, String label, long sessionCount) {}
```

- [ ] **Step 6: Run the focused integration test and compile the backend**

Run the command from Step 2, then `mvn -DskipTests package`.

---

### Task 3: Emit real wall-clock heartbeats from the player

**Files:**
- Modify: `src/analytics/index.ts`
- Modify: `src/components/FeedPlayer.vue`
- Modify: `src/components/FeedPlayer.spec.ts`
- Modify: `.env.example`

**Interfaces:**
- `track` adds `eventId` automatically and accepts optional `playSessionId`.
- `FeedPlayer` emits five-second `video_play_progress` events with `watchedDeltaMs`, `positionMs`, `durationMs`, and `playbackRate`.

- [ ] **Step 1: Add a failing fake-timer player test**

Trigger `playing`, advance timers by 5,000 ms, and assert:

```ts
expect(track).toHaveBeenCalledWith('video_play_progress', expect.objectContaining({
  videoId: 'video-1',
  playSessionId: expect.any(String),
  properties: expect.objectContaining({ watchedDeltaMs: 5000 }),
}));
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run: `npm test -- --run src/components/FeedPlayer.spec.ts`

- [ ] **Step 3: Extend the analytics event envelope**

Generate `eventId: createUuid()` for every queued event and carry `playSessionId` as a top-level field. Enable analytics unless `VITE_ANALYTICS_ENABLED=false` is explicitly configured.

- [ ] **Step 4: Implement the player watch clock**

Use `performance.now()` while playing. Flush the pending wall-clock delta every five seconds and before pause, waiting, seek, visibility change, video switch, end, and unmount. Starting a replay creates a new playback-session ID.

- [ ] **Step 5: Run the focused tests, typecheck, and build**

Run `npm test -- --run src/components/FeedPlayer.spec.ts`, `npm run typecheck`, and `npm run build`.

---

### Task 4: Render and validate actual watch-time metrics

**Files:**
- Modify: `src/api/types.ts`
- Modify: `src/views/DiscoverView.vue`
- Modify: `src/views/DiscoverView.spec.ts`

**Interfaces:**
- Consumes the Task 2 discovery response.
- Renders accumulated actual watch time and session-duration distribution.

- [ ] **Step 1: Update the component test fixture and expectations**

Use `totalWatchTimeMs`, `effectivePlayCount`, `averageWatchTimeMs`, and `sessionCount`; assert the page contains `累计观看时长` and `实际观看会话分布`.

- [ ] **Step 2: Run the focused test and confirm it fails**

Run: `npm test -- --run src/views/DiscoverView.spec.ts`.

- [ ] **Step 3: Update types and dashboard copy**

Replace the old media-duration summary calculation with watch-time formatting; keep the existing layout, search dropdown, and result modal.

- [ ] **Step 4: Run frontend tests, typecheck, and build**

Run `npm test -- --run src/views/DiscoverView.spec.ts src/components/FeedPlayer.spec.ts`, `npm run typecheck`, and `npm run build`.

- [ ] **Step 5: Perform rendered QA**

With the Browser plugin when available, otherwise Playwright, verify `/videoshare/h5/discover` at desktop and 390×844 mobile sizes: page identity, nonblank content, no framework overlay, console health, actual-watch-time labels, search interaction, result modal, and screenshots.
