package com.yingliu.framework.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingliu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiCryptoServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PATH = "/api/v1/crypto/test/echo";
    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");

    @Test
    void decryptsRequestEncryptsResponseAndRejectsReplay() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        ApiCryptoService service = new ApiCryptoService(
                JSON, Clock.fixed(NOW, ZoneOffset.UTC), keyPair, "test-key", Duration.ofMinutes(2));
        JsonNode payload = JSON.readTree("{\"message\":\"hello\"}");
        ClientRequest client = encryptRequest(keyPair, payload, NOW.toEpochMilli());

        ApiCryptoService.DecryptedRequest decrypted = service.decrypt("POST", PATH, "", client.request());

        assertThat(decrypted.body()).isEqualTo(payload);
        ApiCryptoService.EncryptedResponse response = service.encrypt(
                "POST", PATH, "", decrypted, JSON.createObjectNode().put("message", "hello"));
        assertThat(decryptResponse(client.key(), response)).isEqualTo(payload);
        assertThatThrownBy(() -> service.decrypt("POST", PATH, "", client.request()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("CRYPTO_REPLAYED_REQUEST");
    }

    @Test
    void rejectsStaleRequest() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        ApiCryptoService service = new ApiCryptoService(
                JSON, Clock.fixed(NOW, ZoneOffset.UTC), keyPair, "test-key", Duration.ofMinutes(2));
        ClientRequest client = encryptRequest(keyPair, JSON.createObjectNode(), NOW.minusSeconds(121).toEpochMilli());

        assertThatThrownBy(() -> service.decrypt("POST", PATH, "", client.request()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("CRYPTO_STALE_REQUEST");
    }

    @Test
    void usesRedisAtomicInsertForReplayProtectionWhenAvailable() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        AtomicReference<String> claimedKey = new AtomicReference<>();
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class<?>[]{ValueOperations.class},
                (proxy, method, arguments) -> {
                    if ("setIfAbsent".equals(method.getName())) {
                        claimedKey.set((String) arguments[0]);
                        return true;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        StringRedisTemplate redis = new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                return values;
            }
        };
        ApiCryptoService service = new ApiCryptoService(
                JSON, Clock.fixed(NOW, ZoneOffset.UTC), keyPair, "test-key", Duration.ofMinutes(2), redis);
        ClientRequest client = encryptRequest(keyPair, JSON.createObjectNode(), NOW.toEpochMilli());

        service.decrypt("POST", PATH, "", client.request());

        assertThat(claimedKey).hasValue("yingliu:api-crypto:replay:" + client.request().requestId());
    }

    private static ClientRequest encryptRequest(KeyPair keyPair, JsonNode payload, long timestamp) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        byte[] iv = new byte[12];
        java.security.SecureRandom.getInstanceStrong().nextBytes(iv);
        String requestId = UUID.randomUUID().toString();
        byte[] aad = aad(timestamp, requestId);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        aes.updateAAD(aad);
        byte[] ciphertext = aes.doFinal(JSON.writeValueAsBytes(payload));

        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.ENCRYPT_MODE, keyPair.getPublic(), oaep());
        byte[] encryptedKey = rsa.doFinal(key.getEncoded());
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        return new ClientRequest(key, new ApiCryptoService.EncryptedRequest(
                "1", "test-key", "RSA-OAEP-256", "A256GCM", requestId, timestamp,
                b64.encodeToString(encryptedKey), b64.encodeToString(iv), b64.encodeToString(ciphertext)));
    }

    private static JsonNode decryptResponse(SecretKey key, ApiCryptoService.EncryptedResponse response) throws Exception {
        Base64.Decoder b64 = Base64.getUrlDecoder();
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, b64.decode(response.iv())));
        aes.updateAAD(aad(response.timestamp(), response.requestId()));
        return JSON.readTree(aes.doFinal(b64.decode(response.ciphertext())));
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

    private record ClientRequest(SecretKey key, ApiCryptoService.EncryptedRequest request) {
    }
}
