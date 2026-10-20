# Admin API Encryption Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Encrypt every B-client admin API request and response except `multipart/form-data` file uploads.

**Architecture:** Extend the existing RSA-OAEP-256/AES-256-GCM protocol into a transparent fetch transport and a servlet filter. JSON-body requests carry the encrypted envelope in the request body; bodyless requests carry the small envelope in `X-Yingliu-Envelope`; the filter restores the original JSON body for MVC and encrypts the JSON response with the same AES key. Only the known multipart upload routes bypass the filter unchanged.

**Tech Stack:** Java 17, Spring Boot 3.3 servlet filters, Jackson, Web Crypto API, TypeScript 5.6, Vitest.

## Global Constraints

- Encrypt only `/api/v1/admin/**`; keep the public-key endpoint outside that prefix.
- Bypass `multipart/form-data` only on the known video/OSS upload methods and routes, including their responses.
- Preserve the existing HTTP method, URL, query parameters, status code, cookies, and business error envelope.
- Use the existing Base64URL, RSA-OAEP-SHA256, AES-256-GCM, 12-byte IV, replay, and clock-skew rules.
- Add no cryptography dependency.
- Do not modify C-client behavior.

---

### Task 1: Generic browser encrypted fetch

**Files:**
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/api/crypto.ts`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/api/crypto.spec.ts`

**Interfaces:**
- Produces: `createEncryptedFetch(baseUrl?: string, fetcher?: typeof fetch): typeof fetch`.
- Produces: `X-Yingliu-Encrypted: 1` on encrypted calls and `X-Yingliu-Envelope` on bodyless calls.
- Preserves: a normal `Response` containing decrypted JSON for repository callers.

- [x] **Step 1: Write failing transport tests**

```ts
const secureFetch = createEncryptedFetch('/videoshare', serverFetch);
const response = await secureFetch('/videoshare/api/v1/admin/users?page=1');
expect(serverFetch.mock.calls[1][1]?.headers).toEqual(expect.objectContaining({
  'X-Yingliu-Encrypted': '1',
  'X-Yingliu-Envelope': expect.any(String),
}));
expect(await response.json()).toEqual({ code: 'OK', data: { items: [] } });

const form = new FormData();
form.append('file', new File(['video'], 'video.mp4'));
await secureFetch('/videoshare/api/v1/admin/videos/upload', { method: 'POST', body: form });
expect(serverFetch).toHaveBeenLastCalledWith(expect.anything(), expect.objectContaining({ body: form }));
```

- [x] **Step 2: Run tests and confirm the missing factory failure**

Run: `npm test -- src/api/crypto.spec.ts`

- [x] **Step 3: Implement the transparent transport**

```ts
export function createEncryptedFetch(baseUrl = DEFAULT_BASE_URL, fetcher = globalThis.fetch.bind(globalThis)) {
  return async (input: RequestInfo | URL, init?: RequestInit) => {
    if (init?.body instanceof FormData) return fetcher(input, init);
    const method = (init?.method ?? 'GET').toUpperCase();
    const path = protocolPath(input);
    const body = typeof init?.body === 'string' ? JSON.parse(init.body) : null;
    const exchange = await encryptExchange(fetcher, baseUrl, method, path, body, init?.signal);
    const headers = new Headers(init?.headers);
    headers.set('X-Yingliu-Encrypted', '1');
    // Bodyless calls put the envelope in X-Yingliu-Envelope; JSON calls send it as the request body.
    const response = await fetcher(input, encryptedInit(init, headers, exchange.envelope));
    return decryptResponse(response, exchange);
  };
}
```

- [x] **Step 4: Run focused tests and typecheck**

Run: `npm test -- src/api/crypto.spec.ts`
Run: `npm run typecheck`

### Task 2: Admin servlet encryption filter

**Files:**
- Create: `yingliu-api-admin/src/main/java/com/yingliu/api/admin/crypto/AdminApiCryptoFilter.java`
- Test: `yingliu-api-admin/src/test/java/com/yingliu/api/admin/crypto/AdminApiCryptoFilterTest.java`
- Modify: `yingliu-api-admin/src/main/java/com/yingliu/api/admin/security/AdminCorsConfiguration.java`

**Interfaces:**
- Consumes: `ApiCryptoService.decrypt(method, path, authorization, envelope)` and `encrypt(...)`.
- Produces: decrypted `HttpServletRequestWrapper` and encrypted response JSON.
- Bypasses: non-admin paths and `multipart/form-data`.

- [x] **Step 1: Write failing filter tests**

```java
@Test
void decryptsJsonRequestAndEncryptsJsonResponse() throws Exception {
    MockHttpServletRequest request = encryptedRequest("POST", "/api/v1/admin/users", object("name", "alice"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, (req, res) -> {
        assertThat(req.getReader().readLine()).contains("alice");
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("{\"code\":\"OK\",\"data\":{\"id\":\"1\"}}");
    });
    assertThat(clientDecrypt(response.getContentAsString())).contains("\"code\":\"OK\"");
}

@Test
void bypassesMultipartUpload() throws Exception {
    request.setContentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    filter.doFilter(request, response, chain);
    verify(chain).doFilter(request, response);
}
```

- [x] **Step 2: Run the filter test and confirm failure**

Run: `.\\mvnw.cmd -pl yingliu-api-admin -am "-Dtest=AdminApiCryptoFilterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

- [x] **Step 3: Implement request/response wrappers and filter**

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
final class AdminApiCryptoFilter extends OncePerRequestFilter {
    static final String ENCRYPTED = "X-Yingliu-Encrypted";
    static final String ENVELOPE = "X-Yingliu-Envelope";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String contentType = request.getContentType();
        return !request.getRequestURI().startsWith("/api/v1/admin/")
                || (contentType != null && contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE));
    }
}
```

The filter must parse an envelope from the JSON body when present, otherwise decode `X-Yingliu-Envelope`; decrypt using `request.getMethod()` and `request.getRequestURI()`; expose decrypted JSON as `application/json`; capture downstream bytes; parse the captured JSON into `JsonNode`; encrypt it with the same `DecryptedRequest`; and write the encrypted envelope while preserving status and headers. Missing encryption headers return `400 CRYPTO_REQUIRED`. Invalid crypto requests retain the existing crypto error codes.

- [x] **Step 4: Allow encryption headers in CORS and run tests**

```java
.allowedHeaders("Content-Type", "X-Trace-Id", "Idempotency-Key",
        "X-Yingliu-Encrypted", "X-Yingliu-Envelope")
```

Run: `.\\mvnw.cmd -pl yingliu-api-admin -am "-Dtest=AdminApiCryptoFilterTest,ApiCryptoServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

### Task 3: Route all B-client admin traffic through encryption

**Files:**
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/main.ts`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/features/auth/repository.ts`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/features/auth/repository.spec.ts`
- Modify: `D:/PrivateSoftware/gitClaude/yingliu-b/src/views/HealthView.vue`
- Modify: `yingliu-api-admin/src/main/java/com/yingliu/api/admin/auth/AdminAuthController.java`

**Interfaces:**
- Consumes: `createEncryptedFetch` from Task 1.
- Removes: the login controller's one-off crypto handling; the filter owns encryption uniformly.

- [x] **Step 1: Write failing wiring tests**

```ts
expect(encryptedFetch).toHaveBeenCalledFor('/api/v1/admin/auth/session');
expect(encryptedFetch).toHaveBeenCalledFor('/api/v1/admin/auth/login');
expect(encryptedFetch).toHaveBeenCalledFor('/api/v1/admin/auth/logout');
```

- [x] **Step 2: Wire the shared fetch**

```ts
const encryptedFetch = createEncryptedFetch(apiBaseUrl);
const authenticatedFetch = createAuthenticatedFetch(encryptedFetch);
```

`createAdminAuthRepository` must create one encrypted fetch around its supplied raw fetcher before calling the existing `request` helper. `HealthView` must use the same factory. Existing FormData calls remain unchanged because Task 1 bypasses them.

- [x] **Step 3: Restore the login controller to its standard DTO contract**

```java
public ApiResponse<SessionView> login(@Valid @RequestBody LoginRequest body,
                                      HttpServletRequest request,
                                      HttpServletResponse response)
```

The filter decrypts the request before MVC binding and encrypts the `ApiResponse` after controller execution, so the controller retains its normal validation and cookie behavior.

- [x] **Step 4: Run B-client and backend verification**

Run: `npm test`
Run: `npm run typecheck`
Run: `.\\mvnw.cmd -pl yingliu-api-admin -am test`

### Task 4: Cross-project verification

**Files:**
- Modify: `docs/superpowers/plans/2026-09-21-admin-api-encryption.md`

**Interfaces:**
- Verifies: all admin JSON/bodyless requests are encrypted; multipart uploads remain usable.

- [x] **Step 1: Build both projects**

Run: `npm run build`
Run: `.\\mvnw.cmd -pl yingliu-admin -am -DskipTests package`

- [ ] **Step 2: Inspect diffs and runtime behavior**

Run: `git diff --check` in both repositories. Start the rebuilt backend, verify an unencrypted `GET /api/v1/admin/health` returns `CRYPTO_REQUIRED`, verify B-client health/session/login requests succeed, and verify video upload remains multipart.

Automated diff checks, encrypted round trips, context-path handling, login/session routing, and multipart upload bypass all passed. A rebuilt backend on a temporary port also rejected plaintext admin health with `CRYPTO_REQUIRED`. End-to-end browser verification against the user's existing port 8080 process remains unchecked because that process was intentionally not restarted or replaced.

- [x] **Step 3: Mark completed checks in this plan**

Update each successful checkbox from `[ ]` to `[x]`; leave any unverified runtime check unchecked with the exact blocker stated below it.
