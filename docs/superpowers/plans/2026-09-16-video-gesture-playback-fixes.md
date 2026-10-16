# Video Gesture And Playback Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore bidirectional feed swiping and make the C-end video surface support pause/resume, replay, and progress seeking.

**Architecture:** Apply the implementation in sibling project `D:/PrivateSoftware/gitClaude/yingliu-c`. Use Pointer Events for both feed navigation and player interaction so nested controls can stop propagation predictably. Keep feed navigation in `FeedView.vue`; keep media state and seeking in `FeedPlayer.vue`; prove the behavior with the existing mobile Playwright suite.

**Tech Stack:** Vue 3, TypeScript, Vite, Playwright

## Global Constraints

- Preserve all existing uncommitted user changes in the `yingliu-c` worktree.
- Do not add dependencies or change API contracts.
- Maintain one-active-player behavior and existing analytics events.

---

### Task 1: Bidirectional feed pointer gestures

**Files:**
- Modify: `src/views/FeedView.vue`
- Test: `e2e/feed-regression.spec.ts`

**Interfaces:**
- Consumes: existing `next(): Promise<void>` and `prev(): void`
- Produces: pointer handlers that switch only on a dominant vertical movement of at least 60 px

- [x] **Step 1: Extend the feed regression test with upward and downward Pointer Events and verify the current implementation fails.**
- [x] **Step 2: Replace passive touch listeners with primary-pointer tracking, cancel handling, horizontal-gesture rejection, and interactive-control exclusion.**
- [x] **Step 3: Add `touch-action: none` to the feed viewport and run the focused regression test.**

### Task 2: Player pause, replay, and seeking

**Files:**
- Modify: `src/components/FeedPlayer.vue`
- Test: `e2e/feed-regression.spec.ts`

**Interfaces:**
- Consumes: native `HTMLVideoElement.play()`, `pause()`, `currentTime`, `duration`, and Pointer Events
- Produces: full-surface tap control, explicit ended/replay state, and pointer-captured seeking isolated from feed navigation

- [x] **Step 1: Add a Playwright test that stubs media state and fails unless tap-to-play/pause, ended replay, and drag-to-seek all work.**
- [x] **Step 2: Make playback state follow native play, pause, and ended events; restart from zero when replaying an ended video.**
- [x] **Step 3: Bind a full-surface pointer target, reject moved taps, and connect the existing long-press rate behavior.**
- [x] **Step 4: Convert seeking to reactive Pointer Events with pointer capture and event propagation isolation.**
- [x] **Step 5: Run the focused Playwright regression, unit tests, typecheck, and production build.**
