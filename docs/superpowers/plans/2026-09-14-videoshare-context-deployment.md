# Videoshare Context Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `/videoshare` the canonical public context for the admin UI, H5 UI, API, health endpoint, and media URLs, then create one frontend/configuration release archive under `ticktok-api/deploy` without packaging the backend JAR.

**Architecture:** Spring Boot owns the `/videoshare` servlet context. Nginx preserves that prefix when proxying API and health requests, serves prefixed SPA assets from `F:/videoshare/frontend`, and redirects legacy unprefixed browser routes to their canonical equivalents. Both Vite applications emit prefixed asset and API URLs.

**Tech Stack:** Nginx 1.23.4 on Windows, Spring Boot 3, Maven Wrapper, Vue 3, Vite 6, npm, PowerShell ZIP packaging.

## Global Constraints

- Canonical admin URL: `https://www.ysd-hsdy.com.cn/videoshare/admin/login`.
- Canonical H5 URL: `https://www.ysd-hsdy.com.cn/videoshare/h5/feed`.
- Canonical API URL: `https://www.ysd-hsdy.com.cn/videoshare/api/v1/admin/auth/session`.
- Nginx source files remain under `ticktok-api/deploy/nginx`.
- Backend production properties remain under `ticktok-api/deploy/config`.
- Final release archive is written directly under `ticktok-api/deploy`.
- Existing unrelated working-tree changes must be preserved.

---

### Task 1: Canonical frontend bases

**Files:**
- Verify: `D:/PrivateSoftware/gitClaude/yingliu-b/vite.config.ts`
- Verify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/router.ts`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-c/vite.config.ts`
- Create: `D:/PrivateSoftware/gitClaude/yingliu-c/.env.production`

**Interfaces:**
- Consumes: public context `/videoshare`.
- Produces: admin assets under `/videoshare/admin/`, H5 assets under `/videoshare/h5/`, and browser API requests under `/videoshare/api/`.

- [ ] **Step 1: Verify the admin base and router history**

```ts
base: '/videoshare/admin/'
createWebHistory('/videoshare/admin/')
```

- [ ] **Step 2: Configure the H5 build base**

```ts
base: '/videoshare/h5/'
```

- [ ] **Step 3: Configure the H5 production API base**

```env
VITE_API_BASE_URL=/videoshare
VITE_USE_MOCK=false
```

- [ ] **Step 4: Build both frontends**

Run `npm run build` in `yingliu-b` and `yingliu-c`.

- [ ] **Step 5: Verify generated asset prefixes**

Confirm `yingliu-b/dist/index.html` contains `/videoshare/admin/assets/` and `yingliu-c/dist/index.html` contains `/videoshare/h5/assets/`.

### Task 2: Spring Boot service context

**Files:**
- Modify: `D:/PrivateSoftware/gitClaude/ticktok-api/deploy/config/yingliu-env.properties`

**Interfaces:**
- Consumes: requests whose URI starts with `/videoshare`.
- Produces: Spring MVC API routes under `/videoshare/api/**` and generated media URLs under `/videoshare/media/**`.

- [ ] **Step 1: Add the servlet context**

```properties
server.servlet.context-path=/videoshare
```

- [ ] **Step 2: Prefix local media URLs**

```properties
media.local-url-prefix=/videoshare/media
```

- [ ] **Step 3: Set the prefixed WeCom callback**

```properties
yingliu.wecom.redirect-uri=https://www.ysd-hsdy.com.cn/videoshare/h5/login
```

### Task 3: Nginx canonical routing

**Files:**
- Modify: `D:/PrivateSoftware/gitClaude/ticktok-api/deploy/nginx/yingliu.conf`

**Interfaces:**
- Consumes: public `/videoshare/admin/**`, `/videoshare/h5/**`, `/videoshare/api/**`, `/videoshare/health`, and `/videoshare/media/**` requests.
- Produces: static SPA responses or prefix-preserving proxy requests to `127.0.0.1:8080`.

- [ ] **Step 1: Preserve the context for API proxying**

```nginx
location /videoshare/api/ {
    proxy_pass http://127.0.0.1:8080;
}
```

- [ ] **Step 2: Preserve the context for health proxying**

```nginx
location = /videoshare/health {
    proxy_pass http://127.0.0.1:8080;
}
```

- [ ] **Step 3: Canonicalize legacy frontend URLs**

```nginx
location /admin/ { return 301 /videoshare$request_uri; }
location /h5/ { return 301 /videoshare$request_uri; }
```

- [ ] **Step 4: Canonicalize legacy API and media URLs**

```nginx
location /api/ { return 308 /videoshare$request_uri; }
location /media/ { return 301 /videoshare$request_uri; }
```

- [ ] **Step 5: Validate the configuration on the Windows server**

Run `F:/nginx-1.23.4/nginx.exe -t`, then `F:/nginx-1.23.4/nginx.exe -s reload` after deploying the matching frontend and backend artifacts.

### Task 4: Assemble the deployment archive

**Files:**
- Create: `D:/PrivateSoftware/gitClaude/ticktok-api/deploy/yingliu-videoshare-release-<timestamp>.zip`

**Interfaces:**
- Consumes: both frontend `dist` directories, Nginx configuration, and backend production properties.
- Produces: one ZIP with `frontend/admin`, `frontend/h5`, `nginx`, and `config` directories; the backend JAR is intentionally excluded.

- [ ] **Step 1: Copy verified artifacts into a staging directory**

```text
frontend/admin/*
frontend/h5/*
nginx/yingliu.conf
config/yingliu-env.properties
```

- [ ] **Step 2: Compress the staging directory**

Create `deploy/yingliu-videoshare-release-YYYYMMDD-HHMMSS.zip` with optimal compression.

- [ ] **Step 3: Verify archive contents and size**

List the ZIP entries and confirm both `index.html` files, Nginx config, and backend properties are present, and confirm no backend JAR is included.

- [ ] **Step 4: Verify canonical strings in packaged files**

Confirm the archive contains `/videoshare/admin/assets/`, `/videoshare/h5/assets/`, `server.servlet.context-path=/videoshare`, and the prefix-preserving Nginx `proxy_pass` rule.

## Self-Review

- Spec coverage: all four requested requirements map to Tasks 1–4.
- Placeholder scan: the timestamp is an output naming convention resolved by the packaging command; no implementation value is unspecified.
- Type consistency: frontend bases, backend context, and Nginx public prefixes all use the exact string `/videoshare`.
