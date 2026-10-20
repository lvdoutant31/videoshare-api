# Video Publishing Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce and expose the complete video lifecycle: upload a draft, submit and approve it through review, then publish it as a separate action.

**Architecture:** Keep the existing endpoints, but stop auto-approving administrator uploads and stop restoring off-shelf videos directly to published. Creation yields `DRAFT/UNPUBLISHED`; review approval yields `APPROVED/UNPUBLISHED`; only the publish endpoint yields `APPROVED/PUBLISHED`. Restoring an off-shelf video becomes a resubmission to review, and B-side wording reflects both explicit review gates.

**Tech Stack:** Java 17, Spring Boot 3.3, MyBatis-Plus, JUnit 5, MockMvc, Vue 3, TypeScript, Vitest.

**Implementation status:** Completed inline on 2026-09-22. Backend and frontend verification passed; the integration test intentionally disables servlet filters because production admin requests are encrypted before reaching controllers.

## Global Constraints

- State order is `DRAFT -> PENDING_REVIEW -> CLAIMED -> APPROVED -> PUBLISHED`.
- Republish order is `OFF_SHELF -> PENDING_REVIEW -> CLAIMED -> APPROVED -> PUBLISHED`.
- Upload and JSON creation use the same draft state contract.
- Review approval must not publish automatically.
- Publishing before approval must continue to return `STATE_CONFLICT`.
- Existing reject, request-revision, resubmit, scheduled-publish, and off-shelf transitions must remain valid.
- Single and batch restore actions must submit off-shelf videos for review instead of publishing them directly.
- Do not change C-client behavior.
- Do not commit unless the user explicitly requests it.

---

### Task 1: Make every new video a draft

**Files:**
- Modify: `yingliu-service/src/main/java/com/yingliu/service/content/video/DefaultVideoAdminService.java:134`
- Modify: `yingliu-admin/src/test/java/com/yingliu/api/app/AdminVideoIntegrationTest.java:135`

**Interfaces:**
- Consumes: `VideoAdminService.CreateDraftCommand` from both JSON creation and multipart upload.
- Produces: a new video with `auditStatus=DRAFT` and `publishStatus=UNPUBLISHED` and no `AUTO_APPROVED` audit record.

- [ ] **Step 1: Change the existing creation test to require a draft**

Rename `createsListsAndAutoApprovesAdminVideo` to `createsAndListsAdminVideoDraft` and change its assertions:

```java
.andExpect(jsonPath("$.data.auditStatus").value("DRAFT"))
.andExpect(jsonPath("$.data.publishStatus").value("UNPUBLISHED"));

assertEquals(0, jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM video_audit_record WHERE video_id = ? AND action = 'AUTO_APPROVED'",
        Integer.class, Long.parseLong(id)));
```

Replace the previous submit-review conflict assertion with a successful `PENDING_REVIEW` assertion.

- [ ] **Step 2: Run the integration test and confirm it fails on `APPROVED`**

Run:

```powershell
.\mvnw.cmd -pl yingliu-admin -am "-Dtest=AdminVideoIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected before implementation: creation returns `APPROVED`, and an `AUTO_APPROVED` record exists.

- [ ] **Step 3: Remove administrator auto-approval**

In `DefaultVideoAdminService.createDraft`, replace the initial audit state and delete the synthetic approval record:

```java
video.setAuditStatus("DRAFT");
video.setPublishStatus("UNPUBLISHED");
```

Remove the `VideoAuditRecord autoApprove` construction and `auditRecordMapper.insert(autoApprove)` block. Keep category/tag mappings and all validation unchanged.

- [ ] **Step 4: Run the focused integration test**

Run the command from Step 2. Expected: new videos are drafts, can be submitted once, and no automatic approval record is written.

- [ ] **Step 5: Replace manual test fixtures with the real create endpoint**

Refactor `createAndSubmit(String title)` in `AdminVideoIntegrationTest` so it creates the draft through `POST /api/v1/admin/videos`, extracts `data.id`, and then calls `POST /{videoId}/submit-review`. Do not insert a `video` row manually. This ensures all existing review tests exercise the real creation contract.

---

### Task 2: Prove the complete upload-review-publish state sequence

**Files:**
- Modify: `yingliu-admin/src/test/java/com/yingliu/api/app/AdminVideoIntegrationTest.java`
- Verify: `yingliu-service/src/main/java/com/yingliu/service/content/video/mapper/VideoMapper.java:27-51`

The integration test uses `@AutoConfigureMockMvc(addFilters = false)` so plain MockMvc JSON requests exercise the business workflow without the production encryption filter; crypto behavior remains covered by the dedicated filter tests.

**Interfaces:**
- Consumes: existing create/upload, submit-review, claim, approve, and publish endpoints.
- Produces: one integration test proving every externally visible lifecycle state and the publish guard.

- [ ] **Step 1: Add a full lifecycle integration test**

Add `uploadsReviewsAndPublishesVideoInOrder` using the real HTTP endpoints:

```java
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
        .andExpect(jsonPath("$.data.publishStatus").value("PUBLISHED"))
        .andExpect(jsonPath("$.data.publishedAt").isNotEmpty());
```

Use a `createDraft` test helper that posts the same valid payload as the creation test. The multipart upload service already delegates to this same `createDraft` use case, so `VideoUploadServiceTest` remains the focused proof for file persistence.

- [ ] **Step 2: Run the lifecycle test**

Run:

```powershell
.\mvnw.cmd -pl yingliu-admin -am "-Dtest=AdminVideoIntegrationTest#uploadsReviewsAndPublishesVideoInOrder" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: every state assertion passes; the pre-approval publish attempt returns `STATE_CONFLICT`.

- [ ] **Step 3: Run all video service and integration tests**

Run:

```powershell
.\mvnw.cmd -pl yingliu-admin -am "-Dtest=AdminVideoIntegrationTest,VideoUploadServiceTest,VideoMapperReviewFlowTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: upload storage, review ownership, resubmission, publishing, and scheduling tests all pass.

---

### Task 3: Require review before republishing off-shelf videos

**Files:**
- Modify: `yingliu-service/src/main/java/com/yingliu/service/content/video/mapper/VideoMapper.java:65-68`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/content/video/DefaultVideoAdminService.java:270-303`
- Modify: `yingliu-admin/src/test/java/com/yingliu/api/app/AdminVideoIntegrationTest.java:277-305`

**Interfaces:**
- Consumes: existing single and batch restore endpoints.
- Produces: `OFF_SHELF -> PENDING_REVIEW/UNPUBLISHED`, a `SUBMITTED` audit record, and no direct path back to `PUBLISHED`.

- [ ] **Step 1: Change the off-shelf restoration test contract**

Rename `offShelvesRestoresAndDeletesPublishedVideo` to `offShelfRequiresReviewBeforeRepublish`. After calling `POST /{videoId}/restore`, assert:

```java
.andExpect(status().isOk())
.andExpect(jsonPath("$.data.auditStatus").value("PENDING_REVIEW"))
.andExpect(jsonPath("$.data.publishStatus").value("UNPUBLISHED"))
.andExpect(jsonPath("$.data.publishedAt").doesNotExist());
```

Then prove direct publish is rejected, and only claim, approve, and publish restore the public state:

```java
mockMvc.perform(post("/api/v1/admin/videos/{videoId}/publish", id))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/claim", id)
        .contentType("application/json").content("{\"version\":1}"))
        .andExpect(status().isOk());
mockMvc.perform(post("/api/v1/admin/reviews/videos/{videoId}/approve", id)
        .contentType("application/json").content("{\"currentVersion\":1}"))
        .andExpect(status().isOk());
mockMvc.perform(post("/api/v1/admin/videos/{videoId}/publish", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.publishStatus").value("PUBLISHED"));
```

Assert that a second `SUBMITTED` audit record exists: one for initial publication and one for the post-shelf review.

- [ ] **Step 2: Run the focused test and confirm the current direct restore fails it**

Run:

```powershell
.\mvnw.cmd -pl yingliu-admin -am "-Dtest=AdminVideoIntegrationTest#offShelfRequiresReviewBeforeRepublish" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected before implementation: restore returns `APPROVED/PUBLISHED` instead of `PENDING_REVIEW/UNPUBLISHED`.

- [ ] **Step 3: Change the restore SQL to a review resubmission**

Replace `VideoMapper.restore` with:

```java
@Update("UPDATE video SET audit_status = 'PENDING_REVIEW', publish_status = 'UNPUBLISHED', "
        + "published_at = NULL, off_shelf_at = NULL, off_shelf_reason = NULL, "
        + "review_claimed_by = NULL, review_claimed_at = NULL, updated_by = #{actorAdminId}, updated_at = NOW(3) "
        + "WHERE id = #{videoId} AND deleted = 0 AND audit_status = 'APPROVED' AND publish_status = 'OFF_SHELF'")
int restore(@Param("videoId") long videoId, @Param("actorAdminId") long actorAdminId);
```

Keeping the endpoint and method name avoids breaking existing clients; its result contract changes from direct recovery to review submission.

- [ ] **Step 4: Record resubmission for single and batch restore**

In `DefaultVideoAdminService.restore`, retain the pre-update `Video` and add the existing audit helper after a successful mapper update:

```java
Video video = requireVideo(videoId);
if (videoMapper.restore(videoId, actorAdminId) != 1) throw stateConflict();
insertAuditRecord(video, null, "SUBMITTED", null);
return get(videoId);
```

In `batchRestore`, treat an already pending/unpublished item as idempotently successful; otherwise apply the same mapper update and insert one `SUBMITTED` record:

```java
if ("PENDING_REVIEW".equals(video.getAuditStatus())
        && "UNPUBLISHED".equals(video.getPublishStatus())) return true;
if (videoMapper.restore(id, actorAdminId) != 1) throw stateConflict();
insertAuditRecord(video, null, "SUBMITTED", null);
return true;
```

- [ ] **Step 5: Add batch restore coverage**

Create and publish two approved videos, batch off-shelf them, then call `POST /api/v1/admin/videos/batch/restore`. Assert both IDs succeeded, both database rows are `PENDING_REVIEW/UNPUBLISHED`, and each video has a new `SUBMITTED` audit record.

```java
String first = createAndApprove("批量重审一");
String second = createAndApprove("批量重审二");
for (String id : List.of(first, second)) {
    mockMvc.perform(post("/api/v1/admin/videos/{videoId}/publish", id)).andExpect(status().isOk());
}
mockMvc.perform(post("/api/v1/admin/videos/batch/off-shelf")
        .contentType("application/json")
        .content("{\"videoIds\":[\"" + first + "\",\"" + second
                + "\"],\"reason\":\"批量内容复核\"}"))
        .andExpect(status().isOk());
mockMvc.perform(post("/api/v1/admin/videos/batch/restore")
        .contentType("application/json")
        .content("{\"videoIds\":[\"" + first + "\",\"" + second + "\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.succeededIds.length()").value(2));

for (String id : List.of(first, second)) {
    assertEquals("PENDING_REVIEW", jdbcTemplate.queryForObject(
            "SELECT audit_status FROM video WHERE id = ?", String.class, Long.parseLong(id)));
    assertEquals("UNPUBLISHED", jdbcTemplate.queryForObject(
            "SELECT publish_status FROM video WHERE id = ?", String.class, Long.parseLong(id)));
    assertEquals(2, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM video_audit_record WHERE video_id = ? AND action = 'SUBMITTED'",
            Integer.class, Long.parseLong(id)));
}
```

- [ ] **Step 6: Run the video integration test class**

Run:

```powershell
.\mvnw.cmd -pl yingliu-admin -am "-Dtest=AdminVideoIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: initial publication, single republish, and batch republish all require approval.

---

### Task 4: Align B-side review and publish actions with backend semantics

**Files:**
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/views/VideoReviewManagementView.vue`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/views/VideoManagementView.vue`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/features/video/mockRepository.ts`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/features/review/repository.spec.ts`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/features/video/repository.test.ts`
- Verify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/features/video/model.ts`
- Verify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/features/video/repository.ts`

**Interfaces:**
- Consumes: approval endpoint `/api/v1/admin/reviews/videos/{id}/approve` and publish endpoint `/api/v1/admin/videos/{id}/publish`.
- Produces: review UI that promises approval only; the existing video-management `APPROVED -> publish` action remains the explicit final step; restore actions clearly mean resubmission for review.

- [ ] **Step 1: Add a repository test proving approval does not call publish**

Extend `review/repository.spec.ts` with an approved detail response and history response, then assert:

```ts
const task = await createHttpReviewRepository('', fetcher).decide('21', {
  decision: 'APPROVE', currentVersion: 3,
});

expect(fetcher.mock.calls[0][0]).toBe('/api/v1/admin/reviews/videos/21/approve');
expect(fetcher.mock.calls.some(([url]) => String(url).endsWith('/publish'))).toBe(false);
expect(task.status).toBe('APPROVED');
```

- [ ] **Step 2: Run the focused frontend tests**

Run:

```powershell
npm test -- --run src/features/review/repository.spec.ts src/features/video/repository.test.ts
```

Expected: repository routing tests pass.

- [ ] **Step 3: Correct review-page wording**

In `VideoReviewManagementView.vue` change:

- Confirmation from `通过后将立即发布` to `通过后可在视频管理中发布`.
- Success message from `审核已通过，视频将立即发布` to `审核已通过，可前往视频管理发布`.
- Button label from `通过并发布` to `审核通过`.

Do not call `videoRepository.publish` from the review page; publishing remains a separately permissioned action in video management.

- [ ] **Step 4: Correct off-shelf action wording and mock behavior**

In `VideoManagementView.vue`, retain the existing `restore` repository method but change all user-visible copy:

- `恢复上架` -> `重新提交审核`.
- Row action `恢复` -> `重新审核`.
- `批量恢复` -> `批量提交审核`.
- Confirmation and error messages must say that restored videos enter the review queue and are not immediately republished.

Change `mockRepository.restore` to mirror the backend contract:

```ts
return update(id, {
  status: 'PENDING_REVIEW', auditStatus: 'PENDING_REVIEW',
  publishStatus: 'UNPUBLISHED', offShelfReason: undefined, publishedAt: null,
});
```

Add a repository test that posts to `/restore`, maps the response to `PENDING_REVIEW`, and never invokes `/publish`.

```ts
it('sends an off-shelf video back to review instead of publishing it', async () => {
  const fetcher = vi.fn().mockResolvedValue(response({
    ...detail, auditStatus: 'PENDING_REVIEW', publishStatus: 'UNPUBLISHED', publishedAt: null,
  }));

  const video = await createHttpVideoRepository('', fetcher).restore(detail.id);

  expect(fetcher.mock.calls[0][0]).toBe(`/api/v1/admin/videos/${detail.id}/restore`);
  expect(fetcher.mock.calls.some(([url]) => String(url).endsWith('/publish'))).toBe(false);
  expect(video.status).toBe('PENDING_REVIEW');
});
```

- [ ] **Step 5: Run frontend verification**

Run:

```powershell
npm test
npm run typecheck
npm run build
```

Expected: all B-side tests, type checking, and production build pass.

---

### Task 5: Cross-module regression verification

**Files:**
- Modify: `docs/superpowers/plans/2026-09-22-video-publishing-workflow.md`

**Interfaces:**
- Verifies: draft creation, review gating, explicit publishing, B-side actions, and unchanged upload behavior.

- [ ] **Step 1: Run the backend suite and package**

Run:

```powershell
.\mvnw.cmd -pl yingliu-admin -am test
.\mvnw.cmd -pl yingliu-admin -am -DskipTests package
```

- [ ] **Step 2: Inspect both repository diffs**

Run `git diff --check` in `ticktok-api` and `yingliu-b`. Confirm no C-side files changed and the existing encryption changes remain intact.

- [ ] **Step 3: Mark verified plan steps**

Change each completed checkbox to `[x]`. Leave any runtime-only check unchecked and state the exact blocker beneath it.
