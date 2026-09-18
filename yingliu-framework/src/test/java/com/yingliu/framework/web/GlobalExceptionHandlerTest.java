package com.yingliu.framework.web;

import com.yingliu.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    void returnsSafeEnvelopeForUnexpectedException() throws Exception {
        mockMvc.perform(get("/test/unexpected").header(TraceIdFilter.HEADER, "trace-unexpected-001"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(TraceIdFilter.HEADER, "trace-unexpected-001"))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后再试"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.traceId").value("trace-unexpected-001"));
    }

    @Test
    void preservesSpecificBusinessExceptionResponse() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("资源不存在"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("database connection details must not reach the client");
        }

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "资源不存在");
        }
    }
}
