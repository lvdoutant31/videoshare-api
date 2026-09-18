package com.yingliu.api.admin.test.oss;

import com.yingliu.api.admin.security.AdminPermissionAuthorizer;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.media.storage.MediaStorage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/test/oss")
public class OssTestController {
    private static final long DEFAULT_MAX_SIZE = 500L * 1024 * 1024;
    private final MediaStorage mediaStorage;
    private final AdminPermissionAuthorizer authorizer;
    private final long maxSize;

    public OssTestController(MediaStorage mediaStorage, AdminPermissionAuthorizer authorizer,
                             @Value("${media.max-video-size-bytes:524288000}") long maxSize) {
        this.mediaStorage = mediaStorage;
        this.authorizer = authorizer;
        this.maxSize = maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ApiResponse<Map<String, Object>> upload(@RequestPart MultipartFile file, HttpServletRequest request) {
        authorizer.require("oss-test:upload");
        if (file == null || file.isEmpty()) throw validation("文件不能为空");
        if (file.getSize() > maxSize) throw validation("文件不能超过 " + maxSize / 1024 / 1024 + " MB");
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType().toLowerCase(Locale.ROOT);
        String name = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String extension = extension(name);
        String key = "test-uploads/" + LocalDate.now() + "/" + UUID.randomUUID() + extension;
        try (var input = file.getInputStream()) {
            String url = mediaStorage.store(key, input, file.getSize(), contentType);
            return ApiResponse.ok(Map.of("key", key, "url", url, "fileName", name, "size", file.getSize(), "contentType", contentType), traceId(request));
        } catch (Exception ex) {
            throw new IllegalStateException("OSS 测试文件上传失败", ex);
        }
    }

    private String extension(String name) {
        String value = name.replace('\\', '/');
        int dot = value.lastIndexOf('.');
        if (dot < 0 || dot == value.length() - 1 || value.substring(dot).length() > 16) return "";
        String suffix = value.substring(dot).toLowerCase(Locale.ROOT);
        return suffix.matches("\\.[a-z0-9]+") ? suffix : "";
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
    }
}
