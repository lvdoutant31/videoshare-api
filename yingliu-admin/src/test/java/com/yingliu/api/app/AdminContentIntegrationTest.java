package com.yingliu.api.app;

import com.yingliu.YingliuApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = YingliuApplication.class, properties = {
        "spring.flyway.enabled=true",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,cn.dev33.satoken.dao.SaTokenDaoRedisJackson"
})
@AutoConfigureMockMvc
class AdminContentIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("yingliu-db")
            .withUsername("yingliu_test")
            .withPassword("yingliu_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void managesCategories() throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType("application/json")
                        .content("{\"name\":\"科技\",\"sort\":10}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").isString())
                .andReturn().getResponse().getContentAsString();
        String id = response.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(get("/api/v1/admin/categories").param("keyword", "科技"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("科技"));

        mockMvc.perform(patch("/api/v1/admin/categories/{id}", id)
                        .contentType("application/json")
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(delete("/api/v1/admin/categories/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void rejectsInvalidAndDuplicateTags() throws Exception {
        mockMvc.perform(post("/api/v1/admin/tags")
                        .contentType("application/json")
                        .content("{\"name\":\"后端\",\"heatScore\":1}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/tags")
                        .contentType("application/json")
                        .content("{\"name\":\"后端\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));

        mockMvc.perform(post("/api/v1/admin/tags")
                        .contentType("application/json")
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
