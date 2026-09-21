package com.yingliu.service.moderation;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcModerationServiceTest {

    @Test
    void commentsIncludeTheirVideoAndAuthor() throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        AtomicReference<String> listSql = new AtomicReference<>();

        doAnswer(invocation -> {
            listSql.set(invocation.getArgument(0));
            @SuppressWarnings("unchecked")
            RowMapper<ModerationService.Comment> mapper = invocation.getArgument(1);
            ResultSet row = mock(ResultSet.class);
            when(row.getLong(1)).thenReturn(1L);
            when(row.getLong(2)).thenReturn(11L);
            when(row.getString(3)).thenReturn("测试视频");
            when(row.getLong(4)).thenReturn(21L);
            when(row.getString(5)).thenReturn("测试用户");
            when(row.getString(6)).thenReturn("测试评论");
            when(row.getString(7)).thenReturn("NORMAL");
            when(row.getString(8)).thenReturn("NONE");
            when(row.getLong(9)).thenReturn(2L);
            when(row.getLong(10)).thenReturn(3L);
            when(row.getTimestamp(11)).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 9, 21, 8, 0)));
            when(row.getTimestamp(12)).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 9, 21, 8, 1)));
            return List.of(mapper.mapRow(row, 0));
        }).when(db).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ModerationService.Comment>>any(), any(Object[].class));

        var page = new JdbcModerationService(db).comments(1, 20, null, null, null);

        assertThat(listSql.get()).contains("JOIN video v ON v.id=c.video_id");
        assertThat(listSql.get()).contains("JOIN sys_user u ON u.id=c.user_id");
        assertThat(page.items().get(0).videoTitle()).isEqualTo("测试视频");
        assertThat(page.items().get(0).author()).isEqualTo("测试用户");
    }

    @Test
    void restoreOnlyClearsAHiddenCommentWhenItsVideoStillExists() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForObject(contains("SELECT video_id"), eq(Long.class), any(Object[].class))).thenReturn(11L);
        when(db.queryForObject(contains("SELECT deleted=1"), eq(Boolean.class), any(Object[].class))).thenReturn(false);
        when(db.update(contains("status='PENDING'"), eq(1L), eq(11L))).thenReturn(1);

        new JdbcModerationService(db).restoreComment(1L, 7L);

        verify(db).update(contains("status='PENDING'"), eq(1L), eq(11L));
    }

    @Test
    void restoreRejectsACommentWhoseVideoWasDeleted() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForObject(contains("SELECT video_id"), eq(Long.class), any(Object[].class))).thenReturn(11L);
        when(db.queryForObject(contains("SELECT deleted=1"), eq(Boolean.class), any(Object[].class))).thenReturn(true);

        assertThatThrownBy(() -> new JdbcModerationService(db).restoreComment(1L, 7L))
                .hasMessage("视频已删除，评论不可恢复");
    }
}
