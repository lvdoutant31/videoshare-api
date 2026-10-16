package com.yingliu.api.admin.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingliu.framework.crypto.ApiCryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class AdminContentImageCryptoTest {
    @Test void multipartImageUploadReachesAuthenticationAndControllerWithoutJsonEncryption() throws Exception {
        var filter = new AdminApiCryptoFilter(crypto(), new ObjectMapper());
        var request = new MockHttpServletRequest("POST", "/api/v1/admin/content/images");
        request.setContentType("multipart/form-data; boundary=test");
        var reached = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> reached.set(true));
        assertThat(reached).isTrue();
    }
    @Test void nonMultipartRequestStillRequiresEncryption() throws Exception {
        var filter = new AdminApiCryptoFilter(crypto(), new ObjectMapper());
        var request = new MockHttpServletRequest("POST", "/api/v1/admin/content/images");
        request.setContentType("application/json");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("Unencrypted JSON must be rejected"); });
        assertThat(response.getStatus()).isEqualTo(400);
    }

    private ApiCryptoService crypto() {
        return new ApiCryptoService(new ObjectMapper(), new org.springframework.beans.factory.support.DefaultListableBeanFactory()
                .getBeanProvider(org.springframework.data.redis.core.StringRedisTemplate.class));
    }
}
