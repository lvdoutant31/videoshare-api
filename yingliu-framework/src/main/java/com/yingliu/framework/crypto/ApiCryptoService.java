package com.yingliu.framework.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingliu.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ApiCryptoService {
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private final ObjectMapper json;
    private final Clock clock;
    private final KeyPair keyPair;
    private final String kid;
    private final long maxClockSkewMillis;
    private final StringRedisTemplate redis;
    // ponytail: local fallback keeps framework-only tests usable; deployed application supplies Redis.
    private final Map<String, Long> seenRequestIds = new ConcurrentHashMap<>();
    private final AtomicLong nextLocalCleanupAt = new AtomicLong();

    @Autowired
    public ApiCryptoService(ObjectMapper json, ObjectProvider<StringRedisTemplate> redis) {
        this(json, Clock.systemUTC(), generateKeyPair(), null, Duration.ofMinutes(2), redis.getIfAvailable());
    }

    ApiCryptoService(ObjectMapper json, Clock clock, KeyPair keyPair, String kid, Duration maxClockSkew) {
        this(json, clock, keyPair, kid, maxClockSkew, null);
    }

    ApiCryptoService(
            ObjectMapper json,
            Clock clock,
            KeyPair keyPair,
            String kid,
            Duration maxClockSkew,
            StringRedisTemplate redis) {
        this.json = json;
        this.clock = clock;
        this.keyPair = keyPair;
        this.kid = kid == null ? keyId(keyPair) : kid;
        this.maxClockSkewMillis = maxClockSkew.toMillis();
        this.redis = redis;
    }

    public PublicKeyResponse publicKey() {
        return new PublicKeyResponse(
                "1", kid, "RSA-OAEP-256", "A256GCM", "spki",
                B64.encodeToString(keyPair.getPublic().getEncoded()), clock.millis() + Duration.ofHours(1).toMillis());
    }

    public DecryptedRequest decrypt(String method, String path, String authorization, EncryptedRequest request) {
        validateEnvelope(request);
        long now = clock.millis();
        if (request.timestamp() < now - maxClockSkewMillis || request.timestamp() > now + maxClockSkewMillis) {
            throw failure("CRYPTO_STALE_REQUEST", "加密请求已过期");
        }

        try {
            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
            rsa.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(), OAEP);
            byte[] rawKey = rsa.doFinal(decode(request.encryptedKey()));
            byte[] iv = decode(request.iv());
            if (rawKey.length != 32 || iv.length != 12) {
                throw failure("CRYPTO_INVALID_REQUEST", "加密请求参数无效");
            }
            SecretKey key = new SecretKeySpec(rawKey, "AES");

            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            aes.updateAAD(aad(method, path, request.kid(), request.timestamp(), request.requestId(), authorization));
            JsonNode body = json.readTree(aes.doFinal(decode(request.ciphertext())));

            if (!claimRequestId(request.requestId(), now)) {
                throw failure("CRYPTO_REPLAYED_REQUEST", "加密请求不可重复提交");
            }
            return new DecryptedRequest(body, key, request.kid(), request.requestId(), request.timestamp());
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw failure("CRYPTO_INVALID_REQUEST", "加密请求无效");
        }
    }

    private boolean claimRequestId(String requestId, long now) {
        if (redis != null) {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                    "yingliu:api-crypto:replay:" + requestId, "1", Duration.ofMinutes(5)));
        }
        long nextCleanup = nextLocalCleanupAt.get();
        if (now >= nextCleanup && nextLocalCleanupAt.compareAndSet(nextCleanup, now + Duration.ofMinutes(1).toMillis())) {
            seenRequestIds.entrySet().removeIf(entry -> entry.getValue() < now);
        }
        return seenRequestIds.putIfAbsent(requestId, now + Duration.ofMinutes(5).toMillis()) == null;
    }

    public EncryptedResponse encrypt(
            String method, String path, String authorization, DecryptedRequest request, JsonNode body) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, request.key(), new GCMParameterSpec(128, iv));
            aes.updateAAD(aad(method, path, request.kid(), request.timestamp(), request.requestId(), authorization));
            byte[] ciphertext = aes.doFinal(json.writeValueAsBytes(body));
            return new EncryptedResponse(
                    "1", request.kid(), "A256GCM", request.requestId(), request.timestamp(),
                    B64.encodeToString(iv), B64.encodeToString(ciphertext));
        } catch (Exception error) {
            throw failure("CRYPTO_ENCRYPTION_FAILED", "响应加密失败");
        }
    }

    private void validateEnvelope(EncryptedRequest request) {
        if (request == null
                || !"1".equals(request.v())
                || !kid.equals(request.kid())
                || !"RSA-OAEP-256".equals(request.alg())
                || !"A256GCM".equals(request.enc())
                || request.requestId() == null
                || request.requestId().isBlank()
                || request.requestId().length() > 128
                || request.encryptedKey() == null
                || request.encryptedKey().isBlank()
                || request.iv() == null
                || request.iv().isBlank()
                || request.ciphertext() == null
                || request.ciphertext().isBlank()) {
            throw failure("CRYPTO_INVALID_REQUEST", "不支持的加密请求");
        }
    }

    private static byte[] aad(
            String method, String path, String kid, long timestamp, String requestId, String authorization)
            throws GeneralSecurityException {
        String authHash = authorization == null || authorization.isBlank()
                ? ""
                : B64.encodeToString(MessageDigest.getInstance("SHA-256")
                        .digest(authorization.getBytes(StandardCharsets.UTF_8)));
        return String.join("\n", "v1", method.toUpperCase(), path, kid, Long.toString(timestamp), requestId, authHash)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] decode(String value) {
        return B64_DECODER.decode(value);
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Cannot initialize API encryption", error);
        }
    }

    private static String keyId(KeyPair keyPair) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(keyPair.getPublic().getEncoded());
            return "local-" + B64.encodeToString(digest).substring(0, 12);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }

    private static BusinessException failure(String code, String message) {
        return new BusinessException(code, HttpStatus.BAD_REQUEST, message);
    }

    public record PublicKeyResponse(
            String v, String kid, String alg, String enc, String format, String publicKey, long expiresAt) {
    }

    public record EncryptedRequest(
            String v,
            String kid,
            String alg,
            String enc,
            String requestId,
            long timestamp,
            String encryptedKey,
            String iv,
            String ciphertext) {
    }

    public record EncryptedResponse(
            String v, String kid, String enc, String requestId, long timestamp, String iv, String ciphertext) {
    }

    public record DecryptedRequest(JsonNode body, SecretKey key, String kid, String requestId, long timestamp) {
    }
}
