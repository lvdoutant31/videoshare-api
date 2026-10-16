package com.yingliu.api.app;

import com.yingliu.api.admin.AdminHealthController;
import com.yingliu.framework.web.GlobalExceptionHandler;
import com.yingliu.framework.web.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 健康探针的 MVC 契约测试；只装配路由与公共 Web 能力，不伪造业务数据库。 */
@WebMvcTest(controllers = {ApplicationHealthController.class, AdminHealthController.class})
@Import({TraceIdFilter.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class ApplicationHealthTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesAppHealthWithTraceId() throws Exception {
        mockMvc.perform(get("/api/v1/app/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void exposesInfrastructureHealth() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.service").value("app-api"));
    }

    @Test
    void exposesAdminHealth() throws Exception {
        mockMvc.perform(get("/api/v1/admin/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.service").value("admin-api"));
    }
}
