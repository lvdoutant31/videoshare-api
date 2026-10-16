package com.yingliu.api.admin.content.video;

import com.yingliu.api.admin.security.AdminPermissionAuthorizer;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.content.video.ContentImageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/content/images")
public class AdminContentImageController {
    private final AdminPermissionAuthorizer authorizer;
    private final ContentImageService service;
    public AdminContentImageController(AdminPermissionAuthorizer authorizer, ContentImageService service) {
        this.authorizer = authorizer;
        this.service = service;
    }
    @PostMapping(consumes = "multipart/form-data")
    public ApiResponse<Map<String, String>> upload(@RequestPart MultipartFile file, HttpServletRequest request) {
        // Both creation and editing use this endpoint.
        try { authorizer.require("video:create"); }
        catch (BusinessException ex) {
            if (!"PERMISSION_DENIED".equals(ex.getCode())) throw ex;
            authorizer.require("video:update");
        }
        return ApiResponse.ok(service.upload(file), (String) request.getAttribute(TraceIdFilter.ATTRIBUTE));
    }
}
