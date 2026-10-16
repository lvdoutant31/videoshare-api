package com.yingliu.service.content.video;

import com.yingliu.common.exception.BusinessException;
import com.yingliu.media.storage.MediaStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Uses the same local/OSS storage configuration as video uploads. */
@Service
public class ContentImageService {
    private final MediaStorage storage;
    public ContentImageService(MediaStorage storage) { this.storage = storage; }

    public Map<String, String> upload(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) {
            throw invalid("图片不能为空且不能超过 10 MB");
        }
        String format;
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("仅支持 JPEG、PNG、GIF 图片");
            var reader = readers.next();
            try {
                reader.setInput(input);
                format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if (!java.util.Set.of("jpeg", "png", "gif").contains(format)
                        || (long) reader.getWidth(0) * reader.getHeight(0) > 40000000L) {
                    throw invalid("图片格式或尺寸不合法");
                }
            } finally { reader.dispose(); }
        } catch (BusinessException ex) { throw ex; }
        catch (Exception ex) { throw invalid("图片文件无法读取"); }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
            }
            String sha = HexFormat.of().formatHex(digest.digest());
            String key = "images/" + sha.substring(0, 2) + "/" + sha + "." + format;
            String url;
            if (storage.exists(key)) url = storage.getUrl(key);
            else try (InputStream input = file.getInputStream()) {
                url = storage.store(key, input, file.getSize(), "image/" + format);
            }
            return Map.of("url", url);
        } catch (Exception ex) { throw new IllegalStateException("图片保存失败", ex); }
    }

    private BusinessException invalid(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
