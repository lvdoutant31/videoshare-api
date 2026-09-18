package com.yingliu.service.content.video;

import com.yingliu.domain.content.video.MediaResource;
import com.yingliu.media.storage.MediaStorage;
import com.yingliu.service.content.video.VideoAdminService;
import com.yingliu.service.content.video.mapper.MediaResourceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class VideoUploadServiceTest {

    @Test
    void persistsCallbackUrlAndCreatesVideoInOneTransaction() {
        AtomicReference<MediaResource> inserted = new AtomicReference<>();
        AtomicReference<VideoAdminService.CreateDraftCommand> command = new AtomicReference<>();
        AtomicBoolean committed = new AtomicBoolean();
        MediaResourceMapper mapper = proxy(MediaResourceMapper.class, (proxy, method, args) -> {
            if (method.getName().equals("insert")) {
                MediaResource media = (MediaResource) args[0];
                media.setId(99L);
                inserted.set(media);
                return 1;
            }
            return null;
        });
        VideoAdminService videos = proxy(VideoAdminService.class, (proxy, method, args) -> {
            if (method.getName().equals("createDraft")) {
                command.set((VideoAdminService.CreateDraftCommand) args[0]);
            }
            return null;
        });
        MediaStorage storage = new MediaStorage() {
            @Override public String store(String key, InputStream data, long size, String contentType) {
                return "https://cdn.example/" + key;
            }
            @Override public String getUrl(String key) { return "https://cdn.example/" + key; }
            @Override public boolean exists(String key) { return false; }
            @Override public void delete(String key) { }
        };
        TransactionTemplate transaction = new TransactionTemplate(new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }
            @Override public void commit(TransactionStatus status) { committed.set(true); }
            @Override public void rollback(TransactionStatus status) { }
        });
        VideoUploadService service = new VideoUploadService(mapper, videos, storage, transaction, 1024);

        service.uploadAndCreate(7L, 8L,
                new MockMultipartFile("file", "video.mp4", "video/mp4", "video-data".getBytes()),
                "标题", "简介", "原创", List.of(1L), List.of(2L), "PUBLIC");

        assertThat(inserted.get().getUrl()).startsWith("https://cdn.example/");
        assertThat(inserted.get().getBackupStatus()).isEqualTo("READY");
        assertThat(command.get().originalMediaId()).isEqualTo(99L);
        assertThat(command.get().title()).isEqualTo("标题");
        assertThat(committed).isTrue();
    }

    @Test
    void failedStorageCallbackDoesNotStartDatabaseWrite() {
        MediaResourceMapper mapper = proxy(MediaResourceMapper.class, (proxy, method, args) -> {
            if (method.getName().equals("selectOne")) return null;
            throw new AssertionError("回调失败后不能写入媒体记录");
        });
        VideoAdminService videos = proxy(VideoAdminService.class, (proxy, method, args) -> {
            throw new AssertionError("回调失败后不能创建视频");
        });
        MediaStorage storage = new MediaStorage() {
            @Override public String store(String key, InputStream data, long size, String contentType) {
                throw new IllegalStateException("OSS 回调未返回文件地址");
            }
            @Override public String getUrl(String key) { throw new AssertionError(); }
            @Override public boolean exists(String key) { return false; }
            @Override public void delete(String key) { throw new AssertionError("存储层负责失败上传的清理"); }
        };
        TransactionTemplate transaction = new TransactionTemplate(new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
                throw new AssertionError("回调失败前不应开始数据库事务");
            }
            @Override public void commit(TransactionStatus status) { throw new AssertionError(); }
            @Override public void rollback(TransactionStatus status) { throw new AssertionError(); }
        });
        VideoUploadService service = new VideoUploadService(mapper, videos, storage, transaction, 1024);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.uploadAndCreate(7L, 8L,
                new MockMultipartFile("file", "video.mp4", "video/mp4", "video-data".getBytes()),
                "标题", "简介", "原创", List.of(1L), List.of(2L), "PUBLIC"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("OSS 回调");
    }


    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path mediaDirectory;

    @Test
    void localUploadPersistsPlayableUrlAndRepairsReuploadedMedia() throws Exception {
        AtomicReference<MediaResource> saved = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger inserts = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger updates = new java.util.concurrent.atomic.AtomicInteger();
        MediaResourceMapper mapper = proxy(MediaResourceMapper.class, (proxy, method, args) -> {
            if (method.getName().equals("selectOne")) return saved.get();
            if (method.getName().equals("insert")) {
                MediaResource media = (MediaResource) args[0];
                media.setId(99L);
                saved.set(media);
                inserts.incrementAndGet();
                return 1;
            }
            if (method.getName().equals("updateById")) {
                saved.set((MediaResource) args[0]);
                updates.incrementAndGet();
                return 1;
            }
            return null;
        });
        VideoAdminService videos = proxy(VideoAdminService.class, (proxy, method, args) -> null);
        TransactionTemplate transaction = new TransactionTemplate(new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }
            @Override public void commit(TransactionStatus status) { }
            @Override public void rollback(TransactionStatus status) { }
        });
        var storage = new com.yingliu.media.storage.LocalStorageService(
                mediaDirectory.toString(), "http://localhost:8088/media");
        var service = new VideoUploadService(mapper, videos, storage, transaction, 1024);
        byte[] bytes = "local-video".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "video.mp4", "video/mp4", bytes);

        service.uploadAndCreate(7L, 8L, file, "标题", null, null, List.of(1L), List.of(), "PUBLIC");
        String key = saved.get().getRelativePath();
        assertThat(saved.get().getUrl()).isEqualTo("http://localhost:8088/media/" + key);
        assertThat(java.nio.file.Files.readAllBytes(mediaDirectory.resolve(key))).isEqualTo(bytes);

        // Simulate an existing OSS record whose file has not been downloaded locally.
        saved.get().setUrl("https://old-oss.example/" + key);
        java.nio.file.Files.delete(mediaDirectory.resolve(key));
        service.uploadAndCreate(7L, 8L, file, "重新上传", null, null, List.of(1L), List.of(), "PUBLIC");
        assertThat(java.nio.file.Files.readAllBytes(mediaDirectory.resolve(key))).isEqualTo(bytes);
        assertThat(saved.get().getUrl()).isEqualTo("http://localhost:8088/media/" + key);

        // Reusing bytes must also persist the newly configured public URL.
        var movedStorage = new com.yingliu.media.storage.LocalStorageService(
                mediaDirectory.toString(), "http://192.168.1.20:8088/media");
        new VideoUploadService(mapper, videos, movedStorage, transaction, 1024)
                .uploadAndCreate(7L, 8L, file, "再次上传", null, null, List.of(1L), List.of(), "PUBLIC");
        assertThat(saved.get().getUrl()).isEqualTo("http://192.168.1.20:8088/media/" + key);
        assertThat(inserts.get()).isEqualTo(1);
        assertThat(updates.get()).isEqualTo(2);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
