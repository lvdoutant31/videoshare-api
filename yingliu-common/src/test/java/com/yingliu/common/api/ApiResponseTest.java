package com.yingliu.common.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {
    @Test
    void createsSuccessEnvelope() {
        ApiResponse<String> response = ApiResponse.ok("ready", "trace-1");

        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.data()).isEqualTo("ready");
        assertThat(response.traceId()).isEqualTo("trace-1");
    }
}
