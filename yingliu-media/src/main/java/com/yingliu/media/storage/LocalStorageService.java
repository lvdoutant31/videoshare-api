package com.yingliu.media.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;

/**
 * Local filesystem storage backend. Active when {@code media.storage-type} is "local" or unset.
 */
@Service
@ConditionalOnProperty(name = "media.storage-type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements MediaStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path root;
    private final String urlPrefix;

    public LocalStorageService(String root) {
        this(root, "/media");
    }

    @Autowired
    public LocalStorageService(@Value("${media.storage-root:document/media}") String root,
                               @Value("${media.local-url-prefix:/media}") String urlPrefix) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.urlPrefix = urlPrefix.replaceFirst("/+$", "");
        log.info("Local storage initialized: root={}, urlPrefix={}", this.root, this.urlPrefix);
    }

    @Override
    public String store(String key, InputStream data, long size, String contentType) {
        Path temporary = null;
        try {
            Path target = root.resolve(key).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalArgumentException("Invalid storage key: " + key);
            }
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.copy(data, temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("Stored locally: key={}", key);
            return getUrl(key);
        } catch (IOException ex) {
            throw new IllegalStateException("文件保存失败: " + key, ex);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    @Override
    public String getUrl(String key) {
        return urlPrefix + "/" + key;
    }

    @Override
    public boolean exists(String key) {
        Path target = root.resolve(key).normalize();
        return target.startsWith(root) && Files.exists(target);
    }

    @Override
    public void delete(String key) {
        try {
            Path target = root.resolve(key).normalize();
            if (target.startsWith(root)) {
                Files.deleteIfExists(target);
                log.debug("Deleted locally: key={}", key);
            }
        } catch (IOException ignored) {
        }
    }
}
