package com.yingliu.api.admin.content.video;

import com.yingliu.common.exception.BusinessException;
import com.yingliu.domain.content.video.MediaResource;
import com.yingliu.service.content.video.mapper.MediaResourceMapper;
import com.yingliu.service.content.video.VideoAdminService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

@Service
public class VideoUploadService {
    private static final long DEFAULT_MAX_FILE_SIZE = 500L * 1024 * 1024;
    private static final List<String> VIDEO_CONTENT_TYPES = List.of("video/mp4", "video/webm", "video/quicktime");
    private static final List<String> VIDEO_EXTENSIONS = List.of(".mp4", ".webm", ".mov");

    private final MediaResourceMapper mediaMapper;
    private final VideoAdminService videoService;
    private final Path root;
    private final long maxFileSize;

    public VideoUploadService(MediaResourceMapper mediaMapper, VideoAdminService videoService,
                              @Value("${media.storage-root:document/media}") String root,
                              @Value("${media.max-video-size-bytes:524288000}") long maxFileSize) {
        this.mediaMapper = mediaMapper;
        this.videoService = videoService;
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize > 0 ? maxFileSize : DEFAULT_MAX_FILE_SIZE;
    }

    public VideoAdminService.VideoDetail uploadAndCreate(long adminId, long authorId, MultipartFile file,
            String title, String description, String sourceNote, List<Long> categories, List<Long> tags, String visibility) {
        validateRequest(file, title, categories, tags);
        String original = file.getOriginalFilename() == null ? "video.mp4" : Path.of(file.getOriginalFilename()).getFileName().toString();
        String suffix = extension(original);
        String sha = sha256(file);
        String relative = sha.substring(0, 2) + "/" + sha + suffix;
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw validation("文件名不合法");

        boolean created = false;
        try {
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) {
                file.transferTo(target);
                created = true;
            }
            MediaResource media = new MediaResource();
            media.setResourceType("VIDEO_ORIGINAL");
            media.setStorageState("READY");
            media.setRelativePath(relative);
            media.setContentType(normalizedContentType(file));
            media.setFileSize(file.getSize());
            media.setSha256(sha);
            mediaMapper.insert(media);
            try {
                return videoService.createDraft(new VideoAdminService.CreateDraftCommand(adminId, authorId, media.getId(), null,
                        title, description, sourceNote, categories, tags, visibility));
            } catch (RuntimeException ex) {
                mediaMapper.deleteById(media.getId());
                throw ex;
            }
        } catch (IOException ex) {
            deleteQuietly(target, created);
            throw new IllegalStateException("视频保存失败", ex);
        } catch (RuntimeException ex) {
            deleteQuietly(target, created);
            throw ex;
        }
    }

    private void validateRequest(MultipartFile file, String title, List<Long> categories, List<Long> tags) {
        if (file == null || file.isEmpty()) throw validation("视频文件不能为空");
        if (file.getSize() > maxFileSize) throw validation("视频文件不能超过 " + (maxFileSize / 1024 / 1024) + " MB");
        if (title == null || title.isBlank() || title.trim().length() > 120) throw validation("视频标题不能为空且不能超过 120 个字符");
        if (categories == null || categories.isEmpty() || categories.size() > 3) throw validation("分类数量必须为 1-3 个");
        if (tags != null && tags.size() > 10) throw validation("标签数量不能超过 10 个");
        String contentType = normalizedContentType(file);
        String suffix = extension(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (!VIDEO_CONTENT_TYPES.contains(contentType) || !VIDEO_EXTENSIONS.contains(suffix)) {
            throw validation("仅支持 MP4、WebM 或 MOV 视频文件");
        }
    }

    private String normalizedContentType(MultipartFile file) {
        return file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase(Locale.ROOT) : "";
    }

    private String sha256(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            return hex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("文件校验失败", ex);
        }
    }

    private void deleteQuietly(Path target, boolean onlyIfCreated) {
        if (!onlyIfCreated) return;
        try { Files.deleteIfExists(target); } catch (IOException ignored) { }
    }

    private String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format("%02x", b));
        return out.toString();
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
