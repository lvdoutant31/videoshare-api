package com.yingliu.media.controller;

import com.yingliu.media.storage.MediaStorage;
import com.yingliu.media.storage.OssCallbackVerifier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import java.util.Map;

/** OSS 回调入口，先验证原始请求签名，再解析对象 key 并返回规范资源地址。 */
@RestController
@RequestMapping("/api/v1/media/oss")
@ConditionalOnProperty(name = "media.storage-type", havingValue = "oss")
public class OSSController {
    private final MediaStorage storage;
    private final OssCallbackVerifier callbackVerifier;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OSSController(MediaStorage storage, OssCallbackVerifier callbackVerifier) {
        this.storage = storage;
        this.callbackVerifier = callbackVerifier;
    }

    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> callback(HttpServletRequest request, @RequestBody byte[] body) {
        if (!callbackVerifier.verify(request, body)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OSS 回调签名无效");
        }
        try {
            byte[] response = objectMapper.writeValueAsBytes(callback(parseObject(body)));
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(response.length)
                    .body(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("OSS 回调响应生成失败", ex);
        }
    }

    Map<String, String> callback(String object) {
        if (object.isBlank() || object.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OSS object 不合法");
        }
        return Map.of("url", storage.getUrl(object));
    }

    private String parseObject(byte[] body) {
        return Arrays.stream(new String(body, StandardCharsets.UTF_8).split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(pair -> pair.length == 2 && pair[0].equals("object"))
                .map(pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "OSS object 缺失"));
    }
}
