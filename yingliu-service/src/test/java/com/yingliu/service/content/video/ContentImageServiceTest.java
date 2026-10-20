package com.yingliu.service.content.video;

import com.yingliu.common.exception.BusinessException;
import com.yingliu.media.storage.MediaStorage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ContentImageServiceTest {
    @Test void writesImagesToTheSameLocalStorageProviderAsVideos(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var storage = new com.yingliu.media.storage.LocalStorageService(directory.toString(), "/media");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var result = new ContentImageService(storage).upload(new MockMultipartFile("file", "image.png", "image/png", bytes.toByteArray()));
        String key = result.get("url").substring("/media/".length());
        assertThat(result.get("url")).startsWith("/media/images/");
        assertThat(java.nio.file.Files.readAllBytes(directory.resolve(key))).isEqualTo(bytes.toByteArray());
    }

    @Test void storesValidatedImageUsingVideoStorage() throws Exception {
        MediaStorage storage = mock(MediaStorage.class);
        when(storage.store(anyString(), any(), anyLong(), eq("image/png"))).thenReturn("https://example.com/image.png");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var result = new ContentImageService(storage).upload(new MockMultipartFile("file", "a.png", "image/png", bytes.toByteArray()));
        assertThat(result.get("url")).isEqualTo("https://example.com/image.png");
        verify(storage).store(matches("images/[a-f0-9]{2}/[a-f0-9]{64}\\.png"), any(), anyLong(), eq("image/png"));
    }
    @Test void rejectsPretendImagesBeforeWriting() {
        MediaStorage storage = mock(MediaStorage.class);
        assertThatThrownBy(() -> new ContentImageService(storage).upload(
                new MockMultipartFile("file", "a.png", "image/png", "<script>x</script>".getBytes())))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(storage);
    }
}
