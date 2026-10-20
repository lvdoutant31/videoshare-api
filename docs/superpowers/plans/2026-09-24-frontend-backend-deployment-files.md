# Frontend And Backend Deployment Files Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a repeatable, secret-safe Windows deployment bundle containing the Spring Boot API, admin frontend, H5 frontend, Nginx configuration, startup scripts, and verification instructions.

**Architecture:** Build each existing repository with its native toolchain, then stage immutable outputs under one release directory. Nginx serves both Vite SPAs and uploaded media under `/videoshare`, while proxying API traffic to the Spring Boot process on `127.0.0.1:8080`; environment-specific credentials remain outside the archive until the operator copies and edits the provided template.

**Tech Stack:** PowerShell 5.1+, Java 17, Maven Wrapper 3.9.9, Node.js/npm, Vue 3/Vite, Spring Boot 3.3, Nginx for Windows.

## Global Constraints

- Preserve public paths `/videoshare/admin/`, `/videoshare/h5/`, `/videoshare/api/`, `/videoshare/health`, and `/videoshare/media/`.
- Do not package `.env`, database passwords, Redis passwords, WeCom secrets, or OSS credentials.
- Do not change application business code or existing local development commands.
- The release script must stop on the first failed build and must only replace its own staging directory.

---

### Task 1: Secret-safe production configuration templates

**Files:**
- Create: `deploy/config/yingliu-env.properties.example`
- Create: `deploy/nginx/yingliu.conf.template`

**Interfaces:**
- Consumes: Spring Boot properties from `application.yml` and `application-prod.yml`; Vite base paths from both frontend repositories.
- Produces: A backend property template guarded by `CHANGE_ME` values and an Nginx template with `__PUBLIC_HOST__`, `__DEPLOY_ROOT__`, `__SSL_CERTIFICATE__`, and `__SSL_CERTIFICATE_KEY__` tokens.

- [ ] **Step 1: Write the backend configuration template**

Create a production properties file containing the current servlet context, database, Redis, local media, cookie, session, and WeCom settings. All credentials use `CHANGE_ME` or empty optional values.

- [ ] **Step 2: Write the Nginx configuration template**

Serve the H5 and admin SPAs from `frontend/h5` and `frontend/admin`, proxy API and health traffic unchanged to port 8080, and expose the media directory with range request support.

- [ ] **Step 3: Validate template safety**

Run:

```powershell
Select-String -Path deploy/config/yingliu-env.properties.example -Pattern 'Secret=|password=[^$C]'
```

Expected: no match containing a real credential.

### Task 2: Reproducible release builder

**Files:**
- Create: `deploy/build-release.ps1`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: `yingliu-admin/target/yingliu-admin-0.1.0-SNAPSHOT.jar`, `../yingliu-b/dist`, `../yingliu-c/dist`, and the templates from Task 1.
- Produces: `output/releases/yingliu-<version>/` and `output/releases/yingliu-<version>.zip`.

- [ ] **Step 1: Implement prerequisite and repository checks**

Resolve all three repositories, verify `node`, `npm`, and Java 17 are available, and fail with actionable messages when a required path or tool is missing.

- [ ] **Step 2: Implement native builds**

Run `npm run build` in both frontend repositories and `mvnw.cmd -B -pl yingliu-admin -am package -DskipTests` in the backend repository. Check every native exit code before continuing.

- [ ] **Step 3: Stage the release layout**

Copy the JAR, frontend distributions, startup script, configuration example, and rendered Nginx configuration into a temporary staging directory. Rename the JAR to `yingliu-api.jar`.

- [ ] **Step 4: Create the archive and ignore generated output**

Create the versioned ZIP with `Compress-Archive`; add `/output/releases/` to `.gitignore` so generated binaries are not committed.

- [ ] **Step 5: Exercise a non-building dry run**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/build-release.ps1 -SkipBuild -Version validation
```

Expected: release directory and ZIP contain `api/yingliu-api.jar`, both frontend `index.html` files, Nginx config, configuration example, and scripts.

### Task 3: Operator documentation and smoke verification

**Files:**
- Create: `deploy/README.md`
- Create: `deploy/verify-deployment.ps1`

**Interfaces:**
- Consumes: The staged release layout from Task 2 and a public base URL.
- Produces: Exact install, configuration, startup, Nginx reload, rollback, and health-check procedures.

- [ ] **Step 1: Add deployment verification script**

Request the backend health URL plus the H5 and admin entry pages, require HTTP 2xx, and return a non-zero exit code for any failed endpoint.

- [ ] **Step 2: Document installation and rollback**

Document prerequisites, one-command packaging, server directory layout, copying the backend configuration example, editing Nginx paths, starting the API, reloading Nginx, running verification, and switching back to the previous version.

- [ ] **Step 3: Verify documentation commands and script parsing**

Run:

```powershell
$null = [scriptblock]::Create((Get-Content -Raw deploy/build-release.ps1))
$null = [scriptblock]::Create((Get-Content -Raw deploy/verify-deployment.ps1))
```

Expected: both scripts parse without exceptions.

## Self-Review

- Spec coverage: backend binary, both frontend builds, configuration, Nginx, startup, packaging, verification, and rollback are covered.
- Placeholder scan: `CHANGE_ME` and template tokens are deliberate deployment guards, not missing implementation work.
- Type consistency: release paths used by the builder, Nginx template, verifier, and documentation are identical.
