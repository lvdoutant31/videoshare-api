package com.yingliu.api.app;

import com.yingliu.YingliuApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = YingliuApplication.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
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
