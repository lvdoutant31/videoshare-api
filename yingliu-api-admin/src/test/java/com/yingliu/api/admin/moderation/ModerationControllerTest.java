package com.yingliu.api.admin.moderation;

import com.yingliu.api.admin.security.AdminPermissionAuthorizer;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.framework.web.GlobalExceptionHandler;
import com.yingliu.service.admin.AdminModerationService;
import com.yingliu.service.content.shared.ServicePage;
import com.yingliu.service.moderation.ModerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ModerationControllerTest {
    private ModerationService service;
    private AdminModerationService legacyService;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(ModerationService.class);
        legacyService = mock(AdminModerationService.class);
        var authorizer = mock(AdminPermissionAuthorizer.class);
        when(authorizer.require(anyString())).thenReturn(7L);
        mvc = MockMvcBuilders.standaloneSetup(new ModerationController(service, authorizer),
                        new AdminModerationController(legacyService, authorizer, service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private ModerationService.Report report(String status) {
        var created = LocalDateTime.of(2026, 10, 12, 8, 0);
        return new ModerationService.Report(101, 21, "COMMENT", 31, "VIOLENCE", "举报说明", status, "HIGH", 7L,
                null, null, null, created, created, "原评论", "举报者", "暴力内容", "admin", "原评论快照",
                List.of(new ModerationService.ModerationHistory("REPORT_CLAIM", "admin", created, null)));
    }

    @Test
    void listAndDetailMatchTheAdminFrontendContract() throws Exception {
        when(service.reports(2, 10, "PROCESSING", "COMMENT", "HIGH"))
                .thenReturn(new ServicePage<>(List.of(report("PROCESSING")), 11));
        when(service.report(101)).thenReturn(report("PROCESSING"));
        mvc.perform(get("/api/v1/admin/reports").param("page", "2").param("pageSize", "10")
                        .param("status", "PROCESSING").param("targetType", "COMMENT").param("riskLevel", "HIGH"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items[0].targetSummary").value("原评论"))
                .andExpect(jsonPath("$.data.items[0].reporter").value("举报者"))
                .andExpect(jsonPath("$.data.items[0].reason").value("暴力内容"))
                .andExpect(jsonPath("$.data.items[0].assigneeAdminId").value("7"));
        mvc.perform(get("/api/v1/admin/reports/101")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetSnapshot").value("原评论快照"))
                .andExpect(jsonPath("$.data.history[0].action").value("REPORT_CLAIM"));
    }

    @Test
    void resolveAndRejectAcceptTheirDistinctRequestBodies() throws Exception {
        when(service.report(101)).thenReturn(report("RESOLVED"));
        mvc.perform(post("/api/v1/admin/reports/101/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"RESOLVED\",\"note\":\"已处理\"}"))
                .andExpect(status().isOk());
        verify(service).resolveReport(101, 7, "RESOLVED", "已处理");
        when(service.report(101)).thenReturn(report("REJECTED"));
        mvc.perform(post("/api/v1/admin/reports/101/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"证据不足\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
        verify(service).rejectReport(101, 7, "证据不足");
    }

    @Test
    void missingResolutionAndEmptyDecisionNotesAreRejected() throws Exception {
        mvc.perform(post("/api/v1/admin/reports/101/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"已处理\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/admin/reports/101/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\" \"}"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(service);
    }

    @Test
    void bothResolveRoutesUseTheSameAssigneeProtection() throws Exception {
        doThrow(new BusinessException("REPORT_NOT_CLAIMED_BY_CURRENT_OPERATOR", HttpStatus.CONFLICT, "不属于当前处理人"))
                .when(service).resolveReport(101, 7, "RESOLVED", "已处理");
        for (String root : List.of("/api/v1/admin/reports", "/api/v1/admin/moderation/reports")) {
            mvc.perform(post(root + "/101/resolve").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"resolution\":\"RESOLVED\",\"note\":\"已处理\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_NOT_CLAIMED_BY_CURRENT_OPERATOR"));
        }
        verifyNoInteractions(legacyService);
    }
}
