# WeCom-Only Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make enterprise WeCom the only production login and identity source for the C application.

**Architecture:** Keep the existing OAuth authorization-code flow, browser-bound state cookie, Redis one-time state, and `(corp_id, user_id_hmac)` identity mapping. Disable phone authentication by default while retaining an explicit test-only switch, remove self-unbinding from the C client, fetch the member display name on first login, and use one configurable seven-day App session lifetime.

**Tech Stack:** Spring Boot, WxJava CP 4.6, Sa-Token, Redis, Vue 3, TypeScript, Vitest, Playwright.

## Global Constraints

- Enterprise WeCom is the only production user source.
- Never expose CorpSecret, access tokens, or decrypted WeCom UserId values.
- Keep mock and phone login available only when explicitly enabled for local or automated tests.
- Use HTTPS and secure OAuth state cookies in production.

---

### Task 1: Enforce WeCom-only production authentication

**Files:**
- Modify: `yingliu-api-app/src/main/java/com/yingliu/api/app/auth/AppAuthController.java`
- Modify: `yingliu-api-app/src/main/java/com/yingliu/api/app/security/AppSaTokenConfiguration.java`
- Modify: `yingliu-admin/src/main/resources/application-wecom.yml`
- Modify: `yingliu-admin/src/main/resources/application-prod.yml`
- Test: `yingliu-admin/src/test/java/com/yingliu/api/app/AppApiIntegrationTest.java`
- Test: `yingliu-admin/src/test/java/com/yingliu/config/ProfileConfigurationTest.java`

**Interfaces:**
- Consumes: `yingliu.app.phone-login-enabled`, `yingliu.app.session-timeout-seconds`.
- Produces: disabled phone endpoints by default and one shared session timeout value.

- [x] Add an explicit default-off phone login property and reject both phone endpoints when disabled.
- [x] Configure App Sa-Token and auth responses from the same session timeout property.
- [x] Enable phone login only inside integration-test properties.
- [x] Verify the production profile resolves phone login to false and timeout to 604800 seconds.

### Task 2: Use the WeCom member profile during account creation

**Files:**
- Modify: `yingliu-wecom/src/main/java/com/yingliu/wecom/WecomClient.java`
- Test: `yingliu-wecom/src/test/java/com/yingliu/wecom/WecomClientTest.java`

**Interfaces:**
- Consumes: `WxCpUserService.getById(String)` after OAuth returns a member UserId.
- Produces: `WecomClient.Member.displayName` with the member name or a safe fallback.

- [x] Add a failing unit test for a real member display name.
- [x] Resolve the member profile without requesting phone or email fields.
- [x] Keep a safe fallback when profile lookup is unavailable.
- [x] Run the WeCom module tests.

### Task 3: Remove incompatible C-client authentication paths

**Files:**
- Modify: `src/api/index.ts`
- Modify: `src/stores/auth.ts`
- Modify: `src/views/AccountSettingsView.vue`
- Test: `src/views/LoginView.spec.ts`

**Interfaces:**
- Consumes: existing `getWecomAuthorizeUrl` and `wecomLogin` calls.
- Produces: a C client exposing WeCom login only, with no self-unbind action.

- [x] Remove unused phone-login client functions and store action.
- [x] Remove the self-unbind request and settings action because it would leave the user without a login method.
- [x] Present WeCom as the fixed enterprise identity source.
- [x] Run unit tests, type checking, browser regression, and production build.

### Task 4: Verify the complete flow

**Files:**
- Test: `yingliu-admin/src/test/java/com/yingliu/api/app/AppApiIntegrationTest.java`

**Interfaces:**
- Consumes: authorize URL, state cookie, one-time Redis state, mock WeCom code.
- Produces: authenticated App session bound to exactly one encrypted WeCom identity.

- [x] Verify missing browser state, replayed state, inactive binding, and banned account rejection.
- [x] Verify a successful callback reuses the same local user.
- [x] Run targeted integration tests and confirm the application service remains stopped afterward.
