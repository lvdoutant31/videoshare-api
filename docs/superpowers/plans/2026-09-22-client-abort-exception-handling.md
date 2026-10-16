# Client Abort Exception Handling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Treat abandoned video-stream connections as an expected transport event instead of logging a server error and attempting to write a JSON error into a `video/mp4` response.

**Architecture:** Add a narrow Tomcat `ClientAbortException` handler before the generic exception branch. The handler writes no response body and logs only at debug level, leaving the already-started media response untouched.

**Tech Stack:** Java 17, Spring Boot 3.3 MVC, embedded Tomcat, JUnit 5, MockMvc.

**Implementation status:** Completed inline on 2026-09-22. The focused regression and full framework tests pass.

## Global Constraints

- Do not suppress unrelated `IOException` or application exceptions.
- Do not change media range-request or static-resource configuration.
- Do not attempt to replace a committed media response with an `ApiResponse`.
- Do not commit unless the user explicitly requests it.

---

### Task 1: Ignore expected client disconnects

**Files:**
- Modify: `yingliu-framework/src/main/java/com/yingliu/framework/web/GlobalExceptionHandler.java`
- Modify: `yingliu-framework/src/test/java/com/yingliu/framework/web/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `org.apache.catalina.connector.ClientAbortException` raised while Tomcat writes a response.
- Produces: `void handleClientAbort(ClientAbortException, HttpServletRequest)` with no response conversion.

- [ ] **Step 1: Add a failing media-disconnect regression test**

Add a controller endpoint in `GlobalExceptionHandlerTest` that advertises `video/mp4` and throws a client-abort exception:

```java
@GetMapping(value = "/test/aborted-video", produces = "video/mp4")
void abortedVideo() throws ClientAbortException {
    throw new ClientAbortException(new IOException("Connection reset by peer"));
}
```

Exercise it through the existing MockMvc setup and assert that the exception is resolved without a JSON error body:

```java
mockMvc.perform(get("/test/aborted-video"))
        .andExpect(request().resolvedException(instanceOf(ClientAbortException.class)))
        .andExpect(content().string(""));
```

- [ ] **Step 2: Run the focused test and confirm the current generic handler fails**

Run:

```powershell
.\mvnw.cmd -pl yingliu-framework -am "-Dtest=GlobalExceptionHandlerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected before implementation: the generic handler attempts to serialize `ApiResponse` with the preset `video/mp4` content type.

- [ ] **Step 3: Add the narrow client-abort handler**

Import `org.apache.catalina.connector.ClientAbortException` and add this method before `handleUnexpected`:

```java
@ExceptionHandler(ClientAbortException.class)
public void handleClientAbort(ClientAbortException exception, HttpServletRequest request) {
    String traceId = (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
    log.debug("Client disconnected while response was being written: method={}, uri={}, traceId={}",
            request.getMethod(), request.getRequestURI(), traceId);
}
```

Do not return `ResponseEntity`; returning no value is what prevents the second `HttpMessageNotWritableException`.

- [ ] **Step 4: Run focused and framework tests**

Run:

```powershell
.\mvnw.cmd -pl yingliu-framework -am test
```

Expected: all framework tests pass, the disconnect case has an empty body, and the existing unexpected-exception test still returns a safe JSON 500.

- [ ] **Step 5: Inspect the diff**

Run:

```powershell
git diff --check
git diff -- yingliu-framework/src/main/java/com/yingliu/framework/web/GlobalExceptionHandler.java yingliu-framework/src/test/java/com/yingliu/framework/web/GlobalExceptionHandlerTest.java
```

Expected: only the dedicated exception branch, its imports, and the regression test changed.
