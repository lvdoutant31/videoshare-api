# Local Video Upload Implementation Plan

> **For agentic workers:** Execute this bounded plan inline in the current task; use executing-plans guidance for review and verification.

**Goal:** POST /api/v1/admin/videos stores uploads in a configured local directory, persists a directly playable Nginx URL, and returns that URL through existing admin/App DTOs.
**Architecture:** Reuse MediaStorage and existing media_resource.url. Configure LocalStorageService with a URL prefix. Nginx serves the same directory read-only. Re-uploaded existing content must repair missing local files and refresh the persisted URL.
**Tech Stack:** Java 17, Spring Boot 3, JUnit 5, Nginx, Docker Compose.

## Global Constraints
- Work in D:/PrivateSoftware/gitClaude/ticktok-api (user confirmed); it is not a Git checkout.
- Keep the multipart route and existing frontend response contract.
- Directory and URL prefix are configuration values.
- This change is local debugging only; do not add OSS backup, transcoding, CDN or public deployment.

## Task 1: Local storage and persisted URL
- [x] Add LocalStorageServiceTest for configured directory, absolute URL prefix and traversal rejection.
- [x] Add upload tests using real local storage and a mapper test double to assert file bytes, media_resource.url and duplicate re-upload repair.
- [x] Add media.local-url-prefix (local default http://localhost:8088/media) and inject it into LocalStorageService; preserve its existing one-argument constructor for callers.
- [x] On matching media record, only reuse files that exist in the selected storage. Update existing media rows within the video transaction rather than inserting duplicates.
- [x] Run focused media/upload tests and ProfileConfigurationTest.

## Task 2: Nginx and usage
- [x] Add compose.media.yml with MEDIA_ROOT bind-mounted read-only into Nginx and configurable host port 8088.
- [x] Add docker/nginx/media.conf mapping /media/ to /srv/media/ with MIME types, HEAD/GET/OPTIONS, local CORS and no directory listing.
- [x] Add docs/engineering/local-video-storage.md with upload, URL/DB and Range verification, Windows paths and LAN access.
- [x] Update .env.example, local .env and README.
- [x] Validate Compose and Nginx; test a synthetic MP4 response and a Range request without modifying user video records.
- [x] Report tests and whether the application must be restarted.

## Verification results
- Focused Maven reactor run: BUILD SUCCESS; 14 tests, zero failures/errors/skips.
- Nginx: configuration syntax and validation passed; media container listening on 127.0.0.1:8088.
- HTTP GET/HEAD: 200, video/mp4, exact file bytes; Range: 206, exact requested bytes.
- Upload persistence was tested with mapper/transaction test doubles, not against the live business database. Actual browser decoding was not tested.
- Existing application process was not restarted; restart IDEA application or rebuild its JAR to load the code change.
