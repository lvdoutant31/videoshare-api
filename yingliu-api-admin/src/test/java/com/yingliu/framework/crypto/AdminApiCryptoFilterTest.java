package com.yingliu.framework.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingliu.api.admin.crypto.AdminApiCryptoFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AdminApiCryptoFilterTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PATH = "/api/v1/admin/users";
    private static final String CONTEXT_PATH = "/videoshare";
    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    @Test
    void decryptsJsonRequestAndEncryptsJsonResponse() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        ApiCryptoService service = new ApiCryptoService(
                JSON, Clock.fixed(NOW, ZoneOffset.UTC), keyPair, "test-key", Duration.ofMinutes(2));
        AdminApiCryptoFilter filter = new AdminApiCryptoFilter(service, JSON);
        ClientRequest client = encryptRequest(keyPair, JSON.readTree(
                "{\"body\":{\"name\":\"alice\"},\"query\":\"page=1&keyword=a%20b\"}"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", CONTEXT_PATH + PATH);
        request.setContextPath(CONTEXT_PATH);
        request.setRequestURI(CONTEXT_PATH + PATH);
        request.addHeader(AdminApiCryptoFilter.ENCRYPTED_HEADER, "1");
        request.setContentType(AdminApiCryptoFilter.ENCRYPTED_CONTENT_TYPE);
        request.setContent(JSON.writeValueAsBytes(client.request()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (decryptedRequest, capturedResponse) -> {
            assertThat(decryptedRequest.getReader().readLine()).contains("alice");
            assertThat(decryptedRequest.getParameter("page")).isEqualTo("1");
            assertThat(decryptedRequest.getParameter("keyword")).isEqualTo("a b");
            capturedResponse.setContentType("application/json");
            capturedResponse.getWriter().write("{\"code\":\"OK\",\"data\":{\"id\":\"1\"}}");
        });

        assertThat(response.getContentType()).startsWith(AdminApiCryptoFilter.ENCRYPTED_CONTENT_TYPE);
        ApiCryptoService.EncryptedResponse encrypted =
                JSON.readValue(response.getContentAsByteArray(), ApiCryptoService.EncryptedResponse.class);
        assertThat(decryptResponse(client.key(), encrypted).path("code").asText()).isEqualTo("OK");
    }

    @Test
    void bypassesMultipartUpload() throws Exception {
        ApiCryptoService service = new ApiCryptoService(
                JSON, Clock.fixed(NOW, ZoneOffset.UTC), rsaKeyPair(), "test-key", Duration.ofMinutes(2));
        AdminApiCryptoFilter filter = new AdminApiCryptoFilter(service, JSON);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/videos/upload");
        request.setRequestURI("/api/v1/admin/videos/upload");
        request.setContentType("multipart/form-data; boundary=test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(request, response, (sameRequest, sameResponse) -> {
            called.set(true);
            assertThat(sameRequest).isSameAs(request);
            assertThat(sameResponse).isSameAs(response);
        });

        assertThat(called).isTrue();
    }

    @Test
    void rejectsMultipartContentTypeOutsideUploadRoutes() throws Exception {
        ApiCryptoService service = new ApiCryptoService(
                JSON, Clock.fixed(NOW, ZoneOffset.UTC), rsaKeyPair(), "test-key", Duration.ofMinutes(2));
        AdminApiCryptoFilter filter = new AdminApiCryptoFilter(service, JSON);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        request.setRequestURI(PATH);
        request.setContentType("multipart/form-data; boundary=fake");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(request, response, (sameRequest, sameResponse) -> called.set(true));

        assertThat(called).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(JSON.readTree(response.getContentAsByteArray()).path("code").asText())
                .isEqualTo("CRYPTO_REQUIRED");
    }

    private static ClientRequest encryptRequest(KeyPair keyPair, JsonNode payload) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        byte[] iv = new byte[12];
        java.security.SecureRandom.getInstanceStrong().nextBytes(iv);
        String requestId = UUID.randomUUID().toString();
        byte[] aad = aad(NOW.toEpochMilli(), requestId);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        aes.updateAAD(aad);
        byte[] ciphertext = aes.doFinal(JSON.writeValueAsBytes(payload));
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.ENCRYPT_MODE, keyPair.getPublic(), oaep());
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        return new ClientRequest(key, new ApiCryptoService.EncryptedRequest(
                "1", "test-key", "RSA-OAEP-256", "A256GCM", requestId, NOW.toEpochMilli(),
                b64.encodeToString(rsa.doFinal(key.getEncoded())), b64.encodeToString(iv), b64.encodeToString(ciphertext)));
    }

    private static JsonNode decryptResponse(SecretKey key, ApiCryptoService.EncryptedResponse response) throws Exception {
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(128, Base64.getUrlDecoder().decode(response.iv())));
        aes.updateAAD(aad(response.timestamp(), response.requestId()));
        return JSON.readTree(aes.doFinal(Base64.getUrlDecoder().decode(response.ciphertext())));
    }

    private static byte[] aad(long timestamp, String requestId) {
        return String.join("\n", "v1", "POST", PATH, "test-key", Long.toString(timestamp), requestId, "")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static OAEPParameterSpec oaep() {
        return new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private record ClientRequest(SecretKey key, ApiCryptoService.EncryptedRequest request) { }
}
