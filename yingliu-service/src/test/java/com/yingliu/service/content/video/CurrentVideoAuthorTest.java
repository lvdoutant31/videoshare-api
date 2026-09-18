package com.yingliu.service.content.video;

import com.yingliu.common.exception.BusinessException;
import com.yingliu.domain.content.video.AdminUser;
import com.yingliu.domain.content.video.VideoAuthor;
import com.yingliu.service.content.video.mapper.AdminUserMapper;
import com.yingliu.service.content.video.mapper.VideoAuthorMapper;
import com.yingliu.service.content.video.mapper.MediaResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentVideoAuthorTest {
    private final AdminUserMapper admins = mock(AdminUserMapper.class);
    private final VideoAuthorMapper authors = mock(VideoAuthorMapper.class);
    private final MediaResourceMapper media = mock(MediaResourceMapper.class);
    private final DefaultVideoAdminService service = new DefaultVideoAdminService(
            null, media, authors, admins, null, null, null, null, null, null, null);
    private AdminUser admin;

    @BeforeEach
    void setUp() {
        admin = new AdminUser();
        admin.setId(7L);
        admin.setUserId(8L);
        admin.setStatus("NORMAL");
        admin.setUsername("test-admin");
        when(admins.selectForAuthorBinding(7L)).thenReturn(admin);
        VideoAuthor author = new VideoAuthor();
        author.setId(8L);
        author.setStatus("NORMAL");
        when(authors.selectById(8L)).thenReturn(author);
    }

    @Test
    void resolvesLinkedUserInsteadOfAdminId() {
        assertThat(service.currentAuthorId(7L)).isEqualTo(8L);
        verify(authors, never()).selectById(7L);
    }

    @Test
    void createsAndBindsProfileForUnboundAccount() {
        admin.setUserId(null);
        when(authors.insertAdminAuthor(anyLong(), eq("test-admin"))).thenReturn(1);
        when(admins.bindAuthor(eq(7L), anyLong())).thenReturn(1);
        long authorId = service.currentAuthorId(7L);
        assertThat(authorId).isPositive();
        verify(authors).insertAdminAuthor(authorId, "test-admin");
        verify(admins).bindAuthor(7L, authorId);
    }

    @Test
    void rejectsDisabledAdminAndDisabledAuthor() {
        admin.setStatus("DISABLED");
        assertThatThrownBy(() -> service.currentAuthorId(7L)).hasMessageContaining("后台账号不可用");
        admin.setStatus("NORMAL");
        when(authors.selectById(8L)).thenReturn(new VideoAuthor());
        assertThatThrownBy(() -> service.currentAuthorId(7L)).hasMessageContaining("作者不可用");
    }

    @Test
    void draftCreationIgnoresSuppliedAuthorBeforeValidatingMedia() {
        assertThatThrownBy(() -> service.createDraft(new VideoAdminService.CreateDraftCommand(
                7L, 999L, 10L, null, "测试", "", "", List.of(1L), List.of(), "PUBLIC")))
                .hasMessageContaining("媒体资源不存在");
        verify(authors).selectById(8L);
        verify(authors, never()).selectById(999L);
    }
}
