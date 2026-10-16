package com.yingliu.api.admin.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.framework.crypto.ApiCryptoService;
import com.yingliu.framework.web.TraceIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/** 透明解密管理端请求并加密响应；multipart 文件上传保持原协议。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AdminApiCryptoFilter extends OncePerRequestFilter {
    public static final String ENCRYPTED_HEADER = "X-Yingliu-Encrypted";
    public static final String ENVELOPE_HEADER = "X-Yingliu-Envelope";
    public static final String ENCRYPTED_CONTENT_TYPE = "application/vnd.yingliu.encrypted+json";

    private final ApiCryptoService crypto;
    private final ObjectMapper json;

    public AdminApiCryptoFilter(ApiCryptoService crypto, ObjectMapper json) {
        this.crypto = crypto;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = requestPath(request);
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !(path.equals("/api/v1/admin") || path.startsWith("/api/v1/admin/"))
                || isFileUpload(request, path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"1".equals(request.getHeader(ENCRYPTED_HEADER))) {
            writeFailure(response, request, new BusinessException(
                    "CRYPTO_REQUIRED", HttpStatus.BAD_REQUEST, "管理端接口需要加密传输"));
            return;
        }

        ApiCryptoService.DecryptedRequest decrypted;
        String path = requestPath(request);
        try {
            ApiCryptoService.EncryptedRequest envelope = readEnvelope(request);
            decrypted = crypto.decrypt(
                    request.getMethod(), path, request.getHeader("Authorization"), envelope);
        } catch (BusinessException error) {
            writeFailure(response, request, error);
            return;
        } catch (Exception error) {
            writeFailure(response, request, new BusinessException(
                    "CRYPTO_INVALID_REQUEST", HttpStatus.BAD_REQUEST, "加密请求无效"));
            return;
        }

        JsonNode transport = decrypted.body();
        if (transport == null || !transport.isObject() || !transport.has("body") || !transport.has("query")) {
            writeFailure(response, request, new BusinessException(
                    "CRYPTO_INVALID_REQUEST", HttpStatus.BAD_REQUEST, "加密请求载荷无效"));
            return;
        }
        JsonNode originalBody = transport.get("body");
        byte[] plaintext = originalBody == null || originalBody.isNull()
                ? new byte[0]
                : json.writeValueAsBytes(originalBody);
        HttpServletRequest decryptedRequest = new DecryptedAdminRequest(
                request, plaintext, transport.path("query").asText(""));
        ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
        chain.doFilter(decryptedRequest, captured);

        byte[] responseBody = captured.getContentAsByteArray();
        JsonNode responseJson = responseBody.length == 0 ? json.nullNode() : json.readTree(responseBody);
        ApiCryptoService.EncryptedResponse encrypted = crypto.encrypt(
                request.getMethod(), path, request.getHeader("Authorization"), decrypted, responseJson);
        byte[] encryptedBody = json.writeValueAsBytes(encrypted);

        response.resetBuffer();
        response.setContentType(ENCRYPTED_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(encryptedBody.length);
        response.getOutputStream().write(encryptedBody);
    }

    private static String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private static boolean isFileUpload(HttpServletRequest request, String path) {
        String contentType = request.getContentType();
        if (contentType == null
                || !contentType.toLowerCase().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            return false;
        }
        String method = request.getMethod();
        if ("POST".equalsIgnoreCase(method)) {
            return path.equals("/api/v1/admin/videos/upload")
                    || path.equals("/api/v1/admin/content/images");
        }
        return "PATCH".equalsIgnoreCase(method)
                && path.matches("/api/v1/admin/videos/[^/]+/file");
    }

    private ApiCryptoService.EncryptedRequest readEnvelope(HttpServletRequest request) throws IOException {
        String header = request.getHeader(ENVELOPE_HEADER);
        if (header != null && !header.isBlank()) {
            byte[] encoded = Base64.getUrlDecoder().decode(header);
            return json.readValue(encoded, ApiCryptoService.EncryptedRequest.class);
        }
        return json.readValue(request.getInputStream(), ApiCryptoService.EncryptedRequest.class);
    }

    private void writeFailure(HttpServletResponse response, HttpServletRequest request, BusinessException error)
            throws IOException {
        byte[] body = json.writeValueAsBytes(ApiResponse.fail(
                error.getCode(), error.getMessage(), (String) request.getAttribute(TraceIdFilter.ATTRIBUTE)));
        response.setStatus(error.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private static final class DecryptedAdminRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private final String query;
        private final Map<String, String[]> parameters;

        private DecryptedAdminRequest(HttpServletRequest request, byte[] body, String query) {
            super(request);
            this.body = body;
            this.query = query;
            this.parameters = parseQuery(query);
        }

        @Override
        public String getContentType() {
            return body.length == 0 ? null : MediaType.APPLICATION_JSON_VALUE;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener readListener) { }
                @Override public int read() { return input.read(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public String getQueryString() {
            return query.isEmpty() ? null : query;
        }

        @Override
        public String getParameter(String name) {
            String[] values = parameters.get(name);
            return values == null || values.length == 0 ? null : values[0];
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return parameters;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            return parameters.get(name);
        }

        private static Map<String, String[]> parseQuery(String query) {
            Map<String, java.util.List<String>> values = new LinkedHashMap<>();
            if (!query.isBlank()) {
                for (String pair : query.split("&")) {
                    if (pair.isEmpty()) continue;
                    String[] parts = pair.split("=", 2);
                    String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8);
                    values.computeIfAbsent(name, ignored -> new java.util.ArrayList<>()).add(value);
                }
            }
            Map<String, String[]> result = new LinkedHashMap<>();
            values.forEach((name, items) -> result.put(name, items.toArray(String[]::new)));
            return Collections.unmodifiableMap(result);
        }
    }
}
