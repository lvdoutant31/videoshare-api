package com.yingliu.media.controller;

import com.yingliu.media.storage.OssUploadTool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** OSS 表单签名和中转上传入口；签名算法、临时凭证及 SDK 生命周期由上传工具维护。 */
@RestController
@ConditionalOnProperty(name = "media.storage-type", havingValue = "oss")
public class SignController {
    private final OssUploadTool uploadTool;

    public SignController(OssUploadTool uploadTool) {
        this.uploadTool = uploadTool;
    }

    /** 保留直接访问及前端代理使用的两条签名路径。 */
    @GetMapping({"/get_post_signature_for_oss_upload", "/api/get_post_signature_for_oss_upload"})
    public ResponseEntity<Map<String, String>> getPostSignatureForOssUpload(
            @RequestParam(value = "key", required = false) String objectKey) throws Exception {
        return ResponseEntity.ok(uploadTool.createPostSignature(objectKey));
    }

    @PostMapping(value = "/api/v1/media/oss/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadToOss(@RequestPart("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "文件不能为空"));
        }
        return ResponseEntity.ok(uploadTool.uploadFile(file));
    }
}
