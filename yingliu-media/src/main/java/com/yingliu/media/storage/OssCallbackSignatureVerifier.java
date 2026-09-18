package com.yingliu.media.storage;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OssCallbackSignatureVerifier implements OssCallbackVerifier {
    private static final String PUBLIC_KEY_PREFIX_HTTP = "http://gosspublic.alicdn.com/";
    private static final String PUBLIC_KEY_PREFIX_HTTPS = "https://gosspublic.alicdn.com/";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final Map<String, PublicKey> publicKeys = new ConcurrentHashMap<>();

    @Override
    public boolean verify(HttpServletRequest request, byte[] body) {
        try {
            String authorization = request.getHeader("Authorization");
            String publicKeyHeader = request.getHeader("x-oss-pub-key-url");
            if (isBlank(authorization) || isBlank(publicKeyHeader)) return false;

            String publicKeyUrl = new String(Base64.getDecoder().decode(publicKeyHeader.trim()), StandardCharsets.UTF_8).trim();
            if (!publicKeyUrl.startsWith(PUBLIC_KEY_PREFIX_HTTP)
                    && !publicKeyUrl.startsWith(PUBLIC_KEY_PREFIX_HTTPS)) return false;

            PublicKey publicKey = publicKeys.computeIfAbsent(publicKeyUrl, this::loadPublicKey);
            byte[] signature = Base64.getDecoder().decode(authorization.trim());
            Signature verifier = Signature.getInstance("MD5withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signingBytes(request, body));
            return verifier.verify(signature);
        } catch (Exception ex) {
            return false;
        }
    }

    private PublicKey loadPublicKey(String publicKeyUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(publicKeyUrl))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw new IllegalStateException("OSS public key request failed");
            String pem = new String(response.body(), StandardCharsets.US_ASCII)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] encoded = Base64.getDecoder().decode(pem);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OSS public key request failed", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("OSS public key request failed", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("OSS public key parse failed", ex);
        }
    }

    private byte[] signingBytes(HttpServletRequest request, byte[] body) {
        String path = URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8);
        String query = request.getQueryString();
        String signedPath = path + (isBlank(query) ? "" : "?" + query) + "\n";
        byte[] prefix = signedPath.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(body, 0, result, prefix.length, body.length);
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
