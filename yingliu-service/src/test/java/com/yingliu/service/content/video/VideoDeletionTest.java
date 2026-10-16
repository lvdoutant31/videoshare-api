package com.yingliu.service.content.video;

import com.yingliu.domain.content.video.AdminUser;
import com.yingliu.domain.content.video.Video;
import com.yingliu.service.content.video.mapper.AdminUserMapper;
import com.yingliu.service.content.video.mapper.VideoMapper;
import com.yingliu.service.content.video.mapper.VideoPublishScheduleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.annotations.Select;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoDeletionTest {
    private final VideoMapper videoMapper = mock(VideoMapper.class);
    private final AdminUserMapper adminUserMapper = mock(AdminUserMapper.class);
    private final VideoPublishScheduleMapper publishScheduleMapper = mock(VideoPublishScheduleMapper.class);
    private final DefaultVideoAdminService service = new DefaultVideoAdminService(
            videoMapper, null, null, adminUserMapper, null, null, null, null, null,
            publishScheduleMapper, null);

    @BeforeEach
    void setUp() {
        AdminUser admin = new AdminUser();
        admin.setId(7L);
        admin.setStatus("NORMAL");
        when(adminUserMapper.selectById(7L)).thenReturn(admin);

        Video video = new Video();
        video.setId(11L);
        when(videoMapper.selectById(11L)).thenReturn(video);
        when(videoMapper.deleteVideo(11L, 7L)).thenReturn(1);
    }

    @Test
    void deletingVideoSoftDeletesItsComments() {
        service.delete(11L, 7L, "删除视频");

        verify(videoMapper).deleteCommentsByVideoId(11L, 7L, "删除视频");
    }

    @Test
    void batchDeletingVideoSoftDeletesItsComments() {
        var result = service.batchDelete(List.of(11L), 7L, "删除视频");

        assertThat(result.succeeded()).containsExactly(11L);
        verify(videoMapper).deleteCommentsByVideoId(11L, 7L, "删除视频");
    }

    @Test
    void commentPublishLookupLocksTheVideoRowBeforeInsert() throws Exception {
        String sql = VideoMapper.class.getMethod("selectPublicByIdForUpdate", long.class)
                .getAnnotation(Select.class).value()[0];

        assertThat(sql).contains("WHERE v.id = #{videoId}");
        assertThat(sql).endsWith("FOR UPDATE");
    }
}
