# Video Playback Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a single 720P MP4 transcode path with original-file fallback, reduce frontend preloading, and harden Nginx progressive playback without introducing HLS.

**Architecture:** Reuse the existing `video_transcode` and `media_resource` tables. Uploads enqueue a `720P` record, a local-only scheduled worker invokes FFmpeg and publishes the completed media atomically, and app feed assembly prefers a successful transcode while retaining the original as fallback. The Vue player keeps only the current and immediately adjacent sources attached and assigns differentiated preload modes.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, MySQL 8, FFmpeg CLI, Vue 3, Vitest, Nginx.

## Global Constraints

- Preserve the existing `videoUrl` API field; do not introduce HLS or a multi-source response.
- Enable transcoding only for local media storage and behind `yingliu.worker.video-transcode.enabled`.
- Use one `720P` profile: H.264 Main, AAC, yuv420p, maximum long edge 1280, 30 fps, CRF 24, and `faststart`.
- A missing FFmpeg executable or failed transcode must leave the original upload playable.
- Do not modify or depend on uncommitted files in the user's original workspaces.

---

### Task 1: Persist and select 720P transcode jobs

**Files:**
- Create: `yingliu-domain/src/main/java/com/yingliu/domain/content/video/VideoTranscode.java`
- Create: `yingliu-service/src/main/java/com/yingliu/service/content/video/mapper/VideoTranscodeMapper.java`
- Create: `yingliu-service/src/main/java/com/yingliu/service/content/video/VideoTranscodeService.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/content/video/VideoUploadService.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/content/video/DefaultVideoAppService.java`
- Test: `yingliu-service/src/test/java/com/yingliu/service/content/video/VideoTranscodeServiceTest.java`
- Test: `yingliu-service/src/test/java/com/yingliu/service/content/video/DefaultVideoAppServiceTest.java`

**Interfaces:**
- Produces: `VideoTranscodeService.enqueue(long videoId)`, `claimNext()`, `complete(long transcodeId, MediaResource media)`, `fail(long transcodeId, String code, String message)`, and `readyMediaByVideoIds(Collection<Long>)`.
- Consumes: existing `MediaResourceMapper`, `VideoMapper`, and `video_transcode` schema.

- [x] Write focused tests for enqueue/reset behavior, atomic claiming, completion/failure state, and ready-media selection.
- [x] Run the focused service tests and confirm they fail before the implementation exists.
- [x] Add the entity, mapper, and transactional service with the minimum state transitions required by the worker.
- [x] Enqueue `720P` after upload/create or upload/replace in the same database transaction as the video change.
- [x] Make feed and detail assembly prefer a successful 720P media resource and fall back to the original.
- [x] Run the focused service tests and the complete `yingliu-service` test suite.

### Task 2: Add the local FFmpeg worker

**Files:**
- Create: `yingliu-worker/src/main/java/com/yingliu/worker/video/VideoTranscodeProperties.java`
- Create: `yingliu-worker/src/main/java/com/yingliu/worker/video/VideoTranscodeWorkerConfiguration.java`
- Create: `yingliu-worker/src/main/java/com/yingliu/worker/video/LocalVideoTranscodeWorker.java`
- Test: `yingliu-worker/src/test/java/com/yingliu/worker/video/LocalVideoTranscodeWorkerTest.java`
- Modify: `yingliu-admin/src/main/resources/application-local.yml`
- Modify: `yingliu-admin/src/main/resources/application-prod.yml`
- Modify: `deploy/config/yingliu-env.properties`

**Interfaces:**
- Consumes: `VideoTranscodeService.WorkItem`, local `media.storage-root`, and `MediaStorage.store`.
- Produces: a stored `transcoded/720p/{source-sha}.mp4` resource or a failed job that still permits original playback.

- [x] Write tests for the exact FFmpeg argument list, successful publication, timeout, and nonzero-exit failure.
- [x] Run the worker tests and confirm they fail before implementation.
- [x] Implement the scheduled, property-gated local worker using `ProcessBuilder`, a bounded diagnostic buffer, timeout, temporary output, and cleanup.
- [x] Add disabled-by-default configuration with deployment overrides for the FFmpeg path, delay, and timeout.
- [x] Run worker tests and backend compilation.

### Task 3: Reduce browser preloading and media retention

**Files:**
- Modify: `src/components/FeedPlayer.vue`
- Modify: `src/views/FeedView.vue`
- Test: `src/components/FeedPlayer.spec.ts`

**Interfaces:**
- Consumes: player position relative to `activeIndex`.
- Produces: `preload: 'auto' | 'metadata' | 'none'` and `loadSource: boolean` props.

- [x] Write component tests proving current=`auto`, next=`metadata`, previous=`none`, and distant videos have no `src`.
- [x] Run the focused Vitest test and confirm the new expectations fail.
- [x] Replace the boolean preload prop, detach distant sources, and call `load()` when a source is detached.
- [x] Update the feed bindings without changing swipe or active-play behavior.
- [x] Run Vitest, typecheck, and production build.

### Task 4: Harden Nginx progressive MP4 delivery

**Files:**
- Modify: `deploy/nginx/yingliu.conf`
- Modify: `docker/nginx/media.conf`
- Modify: `docs/engineering/local-video-storage.md`

**Interfaces:**
- Produces: byte-range and CORS response headers plus immutable caching for content-addressed media paths.

- [x] Add `sendfile`, Range/CORS exposure headers, immutable cache headers, and open-file caching without enabling gzip for MP4.
- [x] Document FFmpeg prerequisites, feature switches, retry/fallback behavior, and Range verification commands.
- [x] Run repository assertions over both Nginx files and inspect the final diff.

### Task 5: Integrated verification

**Files:**
- Modify only files required to fix failures caused by Tasks 1-4.

- [x] Run the complete Maven test suite.
- [x] Run frontend Vitest, typecheck, and build.
- [x] Review both worktree diffs for unrelated changes and secrets.
- [x] Record residual deployment requirements: FFmpeg must be installed and the transcode feature explicitly enabled.
