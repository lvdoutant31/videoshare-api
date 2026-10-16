# API Payload Encryption Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a runnable RSA-OAEP/AES-GCM encrypted echo endpoint and matching browser client so the complete request/response protocol can be tested before protecting business APIs.

**Architecture:** Spring exposes one public-key endpoint and one encrypted echo endpoint from the shared framework module. Each browser request creates an AES-256 key, wraps it with RSA-OAEP-SHA256, and binds method/path/request metadata as AES-GCM AAD; the server rejects stale or replayed envelopes and encrypts the response with the same AES key and a fresh IV.

**Tech Stack:** Java 17, Spring Boot 3.3, Jackson, JCA, TypeScript 5.6, Web Crypto API, Vitest.

## Global Constraints

- Preserve HTTPS; payload encryption is an additional layer.
- Use only JDK and browser cryptography APIs; add no dependencies.
- Use Base64URL without padding and 12-byte GCM IVs.
- Keep the test route outside business controllers and do not enable encryption on production business APIs in this slice.
- Keep the existing uncommitted-tool directories out of Git.

---

### Task 1: Backend crypto protocol and test endpoint

**Files:**
- Create: `yingliu-framework/src/main/java/com/yingliu/framework/crypto/ApiCryptoService.java`
- Create: `yingliu-framework/src/main/java/com/yingliu/framework/crypto/ApiCryptoController.java`
- Test: `yingliu-framework/src/test/java/com/yingliu/framework/crypto/ApiCryptoServiceTest.java`

**Interfaces:**
- Produces: `GET /api/v1/crypto/public-key`
- Produces: `POST /api/v1/crypto/test/echo`
- Produces: `ApiCryptoService.decrypt(method, path, authorization, request)` and `encrypt(...)`.

- [x] **Step 1: Write the failing service test**

```java
@Test
void decryptsRequestEncryptsResponseAndRejectsReplay() {
    ApiCryptoService service = ApiCryptoService.forTests(clock);
    var request = clientEncrypt(service.publicKey(), "POST", PATH, payload);
    var decrypted = service.decrypt("POST", PATH, "", request);
    assertThat(decrypted.body()).isEqualTo(payload);
    assertThat(clientDecrypt(service.encrypt("POST", PATH, "", decrypted, payload))).isEqualTo(payload);
    assertThatThrownBy(() -> service.decrypt("POST", PATH, "", request)).isInstanceOf(BusinessException.class);
}
```

- [x] **Step 2: Run test and confirm it fails because the service is absent**

Run: `./mvnw -pl yingliu-framework -am -Dtest=ApiCryptoServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [x] **Step 3: Implement the minimal JCA service and controller**

```java
@GetMapping("/public-key")
ApiResponse<PublicKeyResponse> publicKey(HttpServletRequest request)

@PostMapping("/test/echo")
EncryptedResponse echo(@RequestBody EncryptedRequest body, HttpServletRequest request)
```

- [x] **Step 4: Run framework tests**

Run: `./mvnw -pl yingliu-framework -am test`

### Task 2: C-client Web Crypto transport

**Files:**
- Create: `D:/PrivateSoftware/gitClaude/yingliu-c/src/api/crypto.ts`
- Test: `D:/PrivateSoftware/gitClaude/yingliu-c/src/api/crypto.spec.ts`

**Interfaces:**
- Produces: `encryptedRequest<T>(path: string, body: unknown, options?: EncryptedRequestOptions): Promise<T>`.

- [x] **Step 1: Write a failing mocked round-trip test**

```ts
const result = await encryptedRequest<Echo>('/crypto/test/echo', { message: 'hello' }, { fetcher });
expect(result.message).toBe('hello');
```

- [x] **Step 2: Run the focused Vitest file and confirm failure**

Run: `npm test -- src/api/crypto.spec.ts`

- [x] **Step 3: Implement SPKI import, RSA key wrapping, AES-GCM envelope, AAD, and response decrypt with Web Crypto**

- [x] **Step 4: Run test and typecheck**

Run: `npm test -- src/api/crypto.spec.ts`
Run: `npm run typecheck`

### Task 3: B-client Web Crypto transport

**Files:**
- Create: `D:/PrivateSoftware/gitClaude/yingliu-b/src/api/crypto.ts`
- Test: `D:/PrivateSoftware/gitClaude/yingliu-b/src/api/crypto.spec.ts`

**Interfaces:**
- Produces the same `encryptedRequest` contract as Task 2 for admin-side testing.

- [x] **Step 1: Copy the already-tested dependency-free transport into the separate B-client repository**
- [x] **Step 2: Add the same protocol round-trip test**
- [x] **Step 3: Run focused test and typecheck**

Run: `npm test -- src/api/crypto.spec.ts`
Run: `npm run typecheck`

### Task 4: Cross-project verification and commits

**Files:**
- Modify: `docs/superpowers/plans/2026-09-18-api-payload-encryption.md`

- [x] **Step 1: Run backend, B-client, and C-client test suites**
- [x] **Step 2: Verify Git diffs contain only the plan and crypto implementation**
- [x] **Step 3: Commit each repository on `feature/api-payload-encryption`**
