package com.yingliu.service.moderation;

import com.yingliu.common.exception.BusinessException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.when;

class JdbcModerationServiceTest {

    @Test
    void reportFiltersNormalizeLegacyStatesAndRespectObjectRiskAndPageSize() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        AtomicReference<String> sql = new AtomicReference<>();
        AtomicReference<Object[]> args = new AtomicReference<>();
        doAnswer(invocation -> {
            sql.set(invocation.getArgument(0));
            args.set((Object[]) invocation.getRawArguments()[2]);
            return List.of();
        }).when(db).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ModerationService.Report>>any(), any(Object[].class));

        new JdbcModerationService(db).reports(2, 10, "PROCESSING", "COMMENT", "HIGH");

        assertThat(sql.get()).contains("r.status='CLAIMED'", "r.resolution='REJECTED'", "r.target_type=?", "r.risk_level=?", "LEFT JOIN sys_report_reason", "LIMIT ?,?");
        assertThat(args.get()).containsExactly("PROCESSING", "COMMENT", "HIGH", 10L, 10L);
    }

    @Test
    void claimRejectsALostRaceAndDoesNotWriteASuccessAudit() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        assertThatThrownBy(() -> new JdbcModerationService(db).claimReport(101, 7))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo("REPORT_ALREADY_CLAIMED"));
        verify(db, never()).update(contains("INSERT INTO sys_audit_log"), any(Object[].class));
    }

    @Test
    void claimedReportsCanOnlyBeFinishedByTheirAssigneeAndAreAudited() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.update(contains("AND assignee_admin_id=?"), eq("RESOLVED"), eq("RESOLVED"), eq("处理完毕"), eq(101L), eq(7L))).thenReturn(1);
        new JdbcModerationService(db).resolveReport(101, 7, "RESOLVED", " 处理完毕 ");
        verify(db).update(contains("INSERT INTO sys_audit_log"), anyLong(), eq(7L), eq("REPORT_RESOLVE"), eq("REPORT"), eq(101L), eq("处理完毕"));
        assertThatThrownBy(() -> new JdbcModerationService(db).resolveReport(101, 8, "RESOLVED", "越权处理"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo("REPORT_NOT_CLAIMED_BY_CURRENT_OPERATOR"));
    }

    @Test
    void rejectStoresARejectedStatusAndRecordsTheReason() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.update(contains("AND assignee_admin_id=?"), eq("REJECTED"), eq("REJECTED"), eq("证据不足"), eq(101L), eq(7L))).thenReturn(1);
        new JdbcModerationService(db).rejectReport(101, 7, "证据不足");
        verify(db).update(contains("INSERT INTO sys_audit_log"), anyLong(), eq(7L), eq("REPORT_REJECT"), eq("REPORT"), eq(101L), eq("证据不足"));
    }

    @Test
    void reportDetailsIncludeContextSnapshotAndHistory() throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getLong(1)).thenReturn(101L);
        when(row.getString(3)).thenReturn("COMMENT");
        when(row.getString(15)).thenReturn("被举报评论");
        when(row.getString(16)).thenReturn("举报者");
        when(row.getString(17)).thenReturn("暴力内容");
        when(row.getString(18)).thenReturn("管理员");
        when(row.getString(19)).thenReturn("原评论快照");
        when(row.getTimestamp(13)).thenReturn(Timestamp.valueOf("2026-10-12 08:00:00"));
        when(row.getTimestamp(14)).thenReturn(Timestamp.valueOf("2026-10-12 08:01:00"));
        doAnswer(invocation -> {
            RowMapper<ModerationService.Report> mapper = invocation.getArgument(1);
            return mapper.mapRow(row, 0);
        }).when(db).queryForObject(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ModerationService.Report>>any(), eq(101L));

        var report = new JdbcModerationService(db).report(101);
        assertThat(report.targetSummary()).isEqualTo("被举报评论");
        assertThat(report.reporter()).isEqualTo("举报者");
        assertThat(report.reason()).isEqualTo("暴力内容");
        assertThat(report.assignee()).isEqualTo("管理员");
        assertThat(report.targetSnapshot()).isEqualTo("原评论快照");
        assertThat(report.history()).extracting(ModerationService.ModerationHistory::action).containsExactly("REPORT_CREATED");
    }

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
                .isInstanceOf(com.yingliu.common.exception.BusinessException.class)
                .hasMessage("视频已删除，评论不可恢复")
                .satisfies(error -> assertThat(((com.yingliu.common.exception.BusinessException) error).getCode())
                        .isEqualTo("STATE_CONFLICT"));
    }
}
