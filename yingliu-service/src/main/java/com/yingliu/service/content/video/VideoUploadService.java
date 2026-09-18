package com.yingliu.service.content.video;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.domain.content.video.MediaResource;
import com.yingliu.media.storage.MediaStorage;
import com.yingliu.service.content.video.mapper.MediaResourceMapper;
import com.yingliu.service.content.video.VideoAdminService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

/** 视频上传用例：按内容摘要复用媒体，在数据库事务中创建媒体记录与视频草稿。 */
@Service
public class VideoUploadService {
    private static final long DEFAULT_MAX_FILE_SIZE = 500L * 1024 * 1024;
    private static final List<String> VIDEO_CONTENT_TYPES = List.of("video/mp4", "video/webm", "video/quicktime");
    private static final List<String> VIDEO_EXTENSIONS = List.of(".mp4", ".webm", ".mov");

    private final MediaResourceMapper mediaMapper;
    private final VideoAdminService videoService;
    private final MediaStorage mediaStorage;
    private final TransactionTemplate transactionTemplate;
    private final long maxFileSize;

    public VideoUploadService(MediaResourceMapper mediaMapper, VideoAdminService videoService,
                              MediaStorage mediaStorage, TransactionTemplate transactionTemplate,
                              @Value("${media.max-video-size-bytes:524288000}") long maxFileSize) {
        this.mediaMapper = mediaMapper;
        this.videoService = videoService;
        this.mediaStorage = mediaStorage;
        this.transactionTemplate = transactionTemplate;
        this.maxFileSize = maxFileSize > 0 ? maxFileSize : DEFAULT_MAX_FILE_SIZE;
    }

    /**
     * 存储成功后创建草稿；数据库步骤失败时，仅补偿删除本次新建的存储对象。
     * 已存在的同摘要对象可能被其他视频引用，不能在失败补偿中删除。
     */
    public VideoAdminService.VideoDetail uploadAndCreate(long adminId, long authorId, MultipartFile file,
            String title, String description, String sourceNote, List<Long> categories, List<Long> tags, String visibility) {
        return storeAndSave(file, title, categories, tags, media ->
                videoService.createDraft(new VideoAdminService.CreateDraftCommand(adminId, authorId, media.getId(), null,
                        title, description, sourceNote, categories, tags, visibility)));
    }

    /** Replaces the media on the existing video; metadata and media linkage share one transaction. */
    public VideoAdminService.VideoDetail uploadAndReplace(long videoId, long adminId, MultipartFile file,
            String title, String description, List<Long> categories, List<Long> tags, String visibility) {
        videoService.get(videoId);
        return storeAndSave(file, title, categories, tags, media ->
                videoService.update(videoId, adminId, new VideoAdminService.UpdateCommand(
                        title, description, visibility, categories, tags, media.getId())));
    }

    private VideoAdminService.VideoDetail storeAndSave(MultipartFile file, String title,
            List<Long> categories, List<Long> tags,
            java.util.function.Function<MediaResource, VideoAdminService.VideoDetail> save) {
        validateRequest(file, title, categories, tags);
        String original = file.getOriginalFilename() == null ? "video.mp4" : java.nio.file.Path.of(file.getOriginalFilename()).getFileName().toString();
        String suffix = extension(original);
        String sha = sha256(file);
        String key = sha.substring(0, 2) + "/" + sha + suffix;
        MediaResource existing = mediaMapper.selectOne(new LambdaQueryWrapper<MediaResource>()
                .eq(MediaResource::getRelativePath, key).last("LIMIT 1"));
        boolean created = !mediaStorage.exists(key);
        if (existing != null && !created) {
            // Re-uploading after changing local URL settings must persist the current access URL.
            existing.setUrl(mediaStorage.getUrl(key));
            return saveVideo(existing, save);
        }
        boolean stored = false;
        try {
            String url;
            try (InputStream input = file.getInputStream()) {
                url = mediaStorage.store(key, input, file.getSize(), normalizedContentType(file));
            }
            stored = true;
            MediaResource media = existing == null ? new MediaResource() : existing;
            media.setResourceType("VIDEO_ORIGINAL");
            media.setStorageState("READY");
            media.setRelativePath(key);
            media.setUrl(url);
            media.setContentType(normalizedContentType(file));
            media.setFileSize(file.getSize());
            media.setSha256(sha);
            media.setBackupStatus("READY");
            return saveVideo(media, save);
        } catch (RuntimeException ex) {
            if (stored && created) {
                try { mediaStorage.delete(key); } catch (Exception ignored) { }
            }
            throw ex;
        } catch (Exception ex) {
            if (stored && created) {
                try { mediaStorage.delete(key); } catch (Exception ignored) { }
            }
            throw new IllegalStateException("视频保存失败", ex);
        }
    }

    private VideoAdminService.VideoDetail saveVideo(MediaResource media,
            java.util.function.Function<MediaResource, VideoAdminService.VideoDetail> save) {
        // 媒体记录与草稿必须一起提交；外部文件写入位于数据库事务之外。
        return transactionTemplate.execute(status -> {
            if (media.getId() == null) mediaMapper.insert(media);
            else mediaMapper.updateById(media);
            return save.apply(media);
        });
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

    private String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format("%02x", b));
        return out.toString();
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
