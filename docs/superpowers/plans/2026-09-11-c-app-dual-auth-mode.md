# C App Dual Authentication Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make local-network C-end testing bypass authentication completely while production always uses the full WeCom OAuth login flow and Bearer session.

**Architecture:** Add a local-profile-only backend bypass that resolves every protected App request to one explicitly configured test user. Add an explicit frontend build mode that treats the local test build as authenticated without persisting or sending a token. Keep the existing production WeCom authorization URL, state validation, member identity exchange, and Bearer-token issuance unchanged, and add profile tests that prevent production bypass or mock login.

**Tech Stack:** Java 17, Spring Boot 3, Sa-Token, WxJava, Vue 3, Pinia, Vite, Vitest, Playwright.

## Global Constraints

- Local bypass is effective only when the Spring `local` profile is active.
- Production must force App bypass off, phone login off, WeCom mock off, and secure OAuth state cookies on.
- Production login uses the existing WeCom self-built-app OAuth flow and `Authorization: Bearer <token>` App session.
- The bypass user ID is explicit configuration and must identify an existing normal local user.
- Do not reuse the admin authorization switches for the C-end App domain.

---

### Task 1: Backend local App authorization bypass

**Files:**
- Create: `yingliu-api-app/src/main/java/com/yingliu/api/app/security/AppAuthBypassProperties.java`
- Modify: `yingliu-api-app/src/main/java/com/yingliu/api/app/security/AppActorProvider.java`
- Modify: `yingliu-admin/src/main/resources/application-local.yml`
- Modify: `yingliu-admin/src/main/resources/application-prod.yml`
- Modify: `.env.example`
- Modify: `.env`
- Test: `yingliu-api-app/src/test/java/com/yingliu/api/app/security/AppAuthBypassPropertiesTest.java`

**Interfaces:**
- Produces: `AppAuthBypassProperties.enabled()` and `userId()`.
- Consumes: Spring active profiles and `yingliu.app.auth-disabled`, `yingliu.app.auth-bypass-user-id`.

- [ ] **Step 1: Write failing property tests**

```java
@Test
void localProfileMayEnableBypass() {
    var environment = new MockEnvironment().withProperty("spring.profiles.active", "local");
    environment.setActiveProfiles("local");
    var properties = new AppAuthBypassProperties(environment, true, 2001L);
    assertThat(properties.enabled()).isTrue();
    assertThat(properties.userId()).isEqualTo(2001L);
}

@Test
void productionAlwaysDisablesBypass() {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    assertThat(new AppAuthBypassProperties(environment, true, 2001L).enabled()).isFalse();
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `.\mvnw.cmd -pl yingliu-api-app -am -Dtest=AppAuthBypassPropertiesTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because `AppAuthBypassProperties` does not exist.

- [ ] **Step 3: Implement the local-only properties and actor behavior**

```java
@Component
public final class AppAuthBypassProperties {
    private final boolean enabled;
    private final long userId;

    public AppAuthBypassProperties(Environment environment,
            @Value("${yingliu.app.auth-disabled:false}") boolean requested,
            @Value("${yingliu.app.auth-bypass-user-id:2001}") long userId) {
        if (userId <= 0) throw new IllegalArgumentException("yingliu.app.auth-bypass-user-id must be positive");
        this.enabled = requested && environment.acceptsProfiles(Profiles.of("local"));
        this.userId = userId;
    }

    public boolean enabled() { return enabled; }
    public long userId() { return userId; }
}
```

Update `AppActorProvider.currentUserId()`, `loggedIn()`, and `optionalUserId()` to return the configured bypass identity before consulting Sa-Token. Keep `login`, `logout`, and production token handling unchanged.

- [ ] **Step 4: Configure profiles**

```yaml
# application-local.yml
yingliu:
  app:
    auth-disabled: ${YINGLIU_APP_AUTH_DISABLED:true}
    auth-bypass-user-id: ${YINGLIU_APP_AUTH_BYPASS_USER_ID:2001}

# application-prod.yml
yingliu:
  app:
    auth-disabled: false
```

Set the current local `.env` bypass ID to the existing internal-test user and document generic values in `.env.example`.

- [ ] **Step 5: Run backend tests**

Run: `.\mvnw.cmd -pl yingliu-api-app,yingliu-admin -am test`

Expected: BUILD SUCCESS; production profile assertions confirm bypass is false.

### Task 2: Frontend explicit local bypass build

**Files:**
- Modify: `src/api/http.ts`
- Modify: `src/stores/auth.ts`
- Modify: `.env.example`
- Create: `.env.localtest`
- Modify: `package.json`
- Test: `src/api/http.spec.ts`
- Test: `src/stores/auth.spec.ts`

**Interfaces:**
- Produces: `isAuthDisabled(): boolean` and `isAuthenticated(): boolean` that returns true for the local-test build.
- Consumes: `VITE_APP_AUTH_DISABLED=true|false`.

- [ ] **Step 1: Write failing frontend tests**

```ts
it('treats the explicit local-test build as authenticated without a token', async () => {
  vi.stubEnv('VITE_APP_AUTH_DISABLED', 'true');
  vi.resetModules();
  const { isAuthenticated, tokenStore } = await import('./http');
  tokenStore.clear();
  expect(isAuthenticated()).toBe(true);
});
```

Add an auth-store test proving `restore()` calls `/app/me` in bypass mode even when localStorage has no token.

- [ ] **Step 2: Run focused tests and verify they fail**

Run: `npm test -- --run src/api/http.spec.ts src/stores/auth.spec.ts`

Expected: FAIL because no frontend bypass mode exists.

- [ ] **Step 3: Implement the minimal frontend branch**

```ts
export function isAuthDisabled(): boolean {
  return import.meta.env.VITE_APP_AUTH_DISABLED === 'true';
}

export function isAuthenticated(): boolean {
  return isAuthDisabled() || tokenStore.get() != null;
}
```

Do not add a fake token. In bypass mode, omit the Authorization header, let `restore()` call `/app/me`, and rely on the backend local actor identity.

- [ ] **Step 4: Add explicit build commands**

```json
{
  "scripts": {
    "build:localtest": "vue-tsc -b && vite build --mode localtest",
    "build:prod": "vue-tsc -b && vite build --mode production"
  }
}
```

Set `.env.localtest` to `VITE_APP_AUTH_DISABLED=true`; production remains false by default.

- [ ] **Step 5: Run tests and both builds**

Run: `npm test -- --run src/api/http.spec.ts src/stores/auth.spec.ts`

Run: `npm run build:localtest`

Run: `npm run build:prod`

Expected: tests and both builds pass; only the local-test bundle contains bypass behavior.

### Task 3: Lock production to the complete WeCom flow

**Files:**
- Modify: `yingliu-admin/src/test/java/com/yingliu/config/ProfileConfigurationTest.java`
- Test: `yingliu-wecom/src/test/java/com/yingliu/wecom/WecomClientTest.java`
- Test: `yingliu-admin/src/test/java/com/yingliu/api/app/AppApiIntegrationTest.java`

**Interfaces:**
- Consumes: existing `/api/v1/app/auth/wecom/authorize-url` and `/api/v1/app/auth/wecom/callback` endpoints.
- Produces: profile regression coverage proving production cannot enable bypass/mock/phone login.

- [ ] **Step 1: Extend production profile assertions**

```java
assertThat(env.getProperty("yingliu.app.auth-disabled", Boolean.class)).isFalse();
assertThat(env.getProperty("yingliu.app.phone-login-enabled", Boolean.class)).isFalse();
assertThat(env.getProperty("yingliu.wecom.mock-enabled", Boolean.class)).isFalse();
assertThat(env.getProperty("yingliu.wecom.cookie-secure", Boolean.class)).isTrue();
```

- [ ] **Step 2: Verify the existing OAuth contract tests**

Run: `.\mvnw.cmd -pl yingliu-wecom,yingliu-admin -am test`

Expected: authorization URL includes encoded HTTPS redirect and state; missing/mismatched/reused state fails; valid member code returns a Bearer token; external identities are rejected; production mock is rejected.

- [ ] **Step 3: Verify formal environment requirements**

Production must provide non-secret configuration values `WECOM_ENABLED=true`, `WECOM_CORP_ID`, `WECOM_AGENT_ID`, `WECOM_REDIRECT_URI=https://www.ysd-hsdy.com.cn/videoshare/h5/login`, and a stable 32+ character `WECOM_IDENTITY_KEY`; `WECOM_SECRET` stays outside source control.

### Task 4: Rendered verification and deployable artifacts

**Files:**
- Output: `yingliu-admin/target/yingliu-admin-0.1.0-SNAPSHOT.jar`
- Output: C-end local-test `dist/`
- Output: C-end production `dist/`

**Interfaces:**
- Consumes: completed backend and frontend modes.
- Produces: separate internal-test and production deployment packages.

- [ ] **Step 1: Verify local bypass API**

Run the local profile and request `/api/v1/app/me` without Cookie or Authorization.

Expected: HTTP 200 with the configured bypass user's profile.

- [ ] **Step 2: Verify local rendered flow with Playwright**

The flow under test is: `/h5/feed` loads without a token -> protected profile action opens -> `/app/me` succeeds without a login redirect.

Expected: meaningful page renders, no framework overlay, no authorization console errors, and the protected page shows the configured test user.

- [ ] **Step 3: Verify production bundle behavior**

Open the production login page, click enterprise WeCom login, and assert the browser navigates to the WeCom OAuth authorization host with an encoded HTTPS callback and state.

Expected: no local bypass or mock-code branch is active.

- [ ] **Step 4: Package artifacts and hashes**

Run: `.\mvnw.cmd -pl yingliu-admin -am package -DskipTests`

Run: `npm run build:localtest` and archive as `yingliu-c-localtest-dist-20260911.zip`.

Run: `npm run build:prod` and archive as `yingliu-c-prod-dist-20260911.zip`.

Expected: backend JAR and both frontend packages have recorded SHA-256 hashes.

