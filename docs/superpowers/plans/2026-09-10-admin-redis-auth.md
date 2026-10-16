# Admin Redis Authorization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Require a Redis-backed admin access token for every protected management API and page.

**Architecture:** Use a dedicated Sa-Token `admin` login type that issues `Authorization:: Bearer` tokens stored in Redis. The backend centrally blocks anonymous `/api/v1/admin/**` requests and retains controller-level permission checks; the Vue app stores the token in `sessionStorage`, attaches it to every API request, validates it during bootstrap, and clears it on 401/logout.

**Tech Stack:** Java 17, Spring Boot 3.3, Sa-Token 1.39, Redis, Vue 3, TypeScript, Vite, Nginx.

## Global Constraints

- Backend source is `D:/PrivateSoftware/gitClaude/ticktok-api`.
- Management frontend source is `D:/PrivateSoftware/gitClaude/yingliu-b`.
- Existing JDK 8 services on the server must remain unaffected.
- Public admin endpoints are limited to login and health checks.
- Permission failures return 403; absent or invalid sessions return 401.

---

### Task 1: Dedicated Redis-backed admin session

**Files:**
- Create: `yingliu-api-admin/src/main/java/com/yingliu/api/admin/security/AdminSaTokenConfiguration.java`
- Create: `yingliu-api-admin/src/main/java/com/yingliu/api/admin/security/AdminAuthenticationInterceptor.java`
- Modify: `yingliu-api-admin/src/main/java/com/yingliu/api/admin/auth/AdminAuthService.java`
- Modify: `yingliu-api-admin/src/main/java/com/yingliu/api/admin/auth/AdminAuthController.java`
- Modify: `yingliu-api-admin/src/main/java/com/yingliu/api/admin/security/SaTokenAdminActorProvider.java`
- Modify: `yingliu-api-admin/src/main/java/com/yingliu/api/admin/security/AdminCorsConfiguration.java`

**Interfaces:**
- Produces: login response `{ accessToken, tokenType, expiresIn, session }`.
- Consumes: `Authorization: Bearer <token>` on protected admin requests.

- [ ] Add a named `adminStpLogic` with login type `admin`, header-only Bearer tokens, eight-hour Redis TTL, UUID token style, and no cookie/body token reading.
- [ ] Add an MVC interceptor for `/api/v1/admin/**`, excluding `/auth/login` and `/health`, that returns `UNAUTHORIZED` when `adminStpLogic` has no login.
- [ ] Change login, session, logout, and actor lookup to use `adminStpLogic` instead of global `StpUtil`.
- [ ] Return the generated token from login and allow `Authorization` in CORS.
- [ ] Compile the backend and run the authorization tests.

### Task 2: Token-aware management frontend

**Files:**
- Modify: `src/features/auth/repository.ts`
- Modify: `src/main.ts`
- Modify: `src/router.ts`
- Modify: `src/views/AdminLoginView.vue`
- Test: existing frontend test suites plus auth repository tests.

**Interfaces:**
- Consumes: backend login result `{ accessToken, tokenType, expiresIn, session }`.
- Produces: `Authorization: Bearer <token>` on every protected request.

- [ ] Add session-storage token read/write/clear helpers.
- [ ] Store the login token and attach it to session/logout calls.
- [ ] Attach the token in the shared authenticated fetch and clear state on 401.
- [ ] Keep every management route behind bootstrap session validation.
- [ ] Run TypeScript tests and production build.

### Task 3: Deployment artifacts

**Files:**
- Modify: `tmp/deploy/nginx-yingliu-server.conf.example`
- Create: backend JAR and management frontend ZIP in `tmp/deploy`.

**Interfaces:**
- Produces: deployable JAR and ZIP with SHA256 hashes.

- [ ] Explicitly forward the `Authorization` header through Nginx.
- [ ] Run a clean backend package and copy the executable JAR.
- [ ] Run a clean frontend build and ZIP the `dist` contents.
- [ ] Verify artifact paths, sizes, timestamps, and SHA256 hashes.
