package com.yingliu.api.app.content.video;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yingliu.api.app.security.AppActorProvider;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.domain.content.category.ContentCategory;
import com.yingliu.domain.content.tag.ContentTag;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.app.mapper.AppUserMapper;
import com.yingliu.service.content.category.mapper.ContentCategoryMapper;
import com.yingliu.service.content.tag.mapper.ContentTagMapper;
import com.yingliu.service.content.video.ContentImageService;
import com.yingliu.service.content.video.VideoUploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/app/content")
public class AppContentController {
    private final AppActorProvider actor;
    private final AppUserMapper users;
    private final VideoUploadService upload;
    private final ContentImageService images;
    private final ContentCategoryMapper categories;
    private final ContentTagMapper tags;

    public AppContentController(AppActorProvider actor, AppUserMapper users, VideoUploadService upload,
                                ContentImageService images, ContentCategoryMapper categories, ContentTagMapper tags) {
        this.actor = actor; this.users = users; this.upload = upload;
        this.images = images; this.categories = categories; this.tags = tags;
    }

    @GetMapping("/options")
    public ApiResponse<Map<String, List<Option>>> options(HttpServletRequest request) {
        requirePublisher();
        return ApiResponse.ok(Map.of(
                "categories", categories.selectList(new LambdaQueryWrapper<ContentCategory>()
                        .eq(ContentCategory::getStatus, "ENABLED").orderByAsc(ContentCategory::getSort, ContentCategory::getId))
                        .stream().map(item -> new Option(String.valueOf(item.getId()), item.getName())).toList(),
                "tags", tags.selectList(new LambdaQueryWrapper<ContentTag>().eq(ContentTag::getStatus, "ENABLED")
                        .isNull(ContentTag::getMergedToId).orderByAsc(ContentTag::getId))
                        .stream().map(item -> new Option(String.valueOf(item.getId()), item.getName())).toList()), trace(request));
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Submission> submit(@RequestPart(required = false) MultipartFile file,
            @RequestParam String title, @RequestParam(required = false) String description,
            @RequestParam(required = false) List<Long> categoryIds, @RequestParam(required = false) List<Long> tagIds,
            HttpServletRequest request) {
        long userId = requirePublisher();
        var result = upload.uploadUserSubmission(userId, file, title, description, categoryIds, tagIds);
        return ApiResponse.ok(new Submission(String.valueOf(result.id()), result.auditStatus()), trace(request));
    }

    @PostMapping(value = "/images", consumes = "multipart/form-data")
    public ApiResponse<Map<String, String>> uploadImage(@RequestPart MultipartFile file, HttpServletRequest request) {
        requirePublisher();
        return ApiResponse.ok(images.upload(file), trace(request));
    }

    private long requirePublisher() {
        long id = actor.currentUserId();
        if (users.countNormal(id) != 1) throw new BusinessException("PERMISSION_DENIED", HttpStatus.FORBIDDEN, "当前账号不可发布内容");
        return id;
    }
    private String trace(HttpServletRequest request) { return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE); }
    public record Option(String id, String name) { }
    public record Submission(String id, String auditStatus) { }
}
