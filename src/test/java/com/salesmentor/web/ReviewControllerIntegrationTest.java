package com.salesmentor.web;

import com.salesmentor.agent.application.SalesReviewAgent;
import com.salesmentor.agent.report.ReviewReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReviewControllerIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("salesmentor").withUsername("salesmentor").withPassword("salesmentor");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private MockMvc mvc;
    @MockBean private SalesReviewAgent agent;

    @Test
    void createsExecutesAndReadsReportAndSafeTracesOverHttp() throws Exception {
        when(agent.review(any())).thenReturn(new ReviewReport(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of()));
        String requestId = "http-" + System.nanoTime();
        String body = "{\"requestId\":\"" + requestId
                + "\",\"industry\":\"tech\",\"conversationContent\":\"price objection\",\"reviewGoal\":\"review\"}";

        String response = mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.taskId").isNumber())
                .andReturn().getResponse().getContentAsString();
        long taskId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("taskId").asLong();

        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            String status = mvc.perform(get("/api/reviews/" + taskId)).andReturn().getResponse().getContentAsString();
            if (status.contains("\"SUCCEEDED\"")) break;
            Thread.sleep(50);
        }
        mvc.perform(get("/api/reviews/" + taskId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.report").exists())
                .andExpect(jsonPath("$.conversationContent").doesNotExist());
        mvc.perform(get("/api/reviews/" + taskId + "/traces")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stepNo").value(1))
                .andExpect(jsonPath("$[0].inputJson").doesNotExist())
                .andExpect(jsonPath("$[0].evidenceIds").doesNotExist())
                .andExpect(jsonPath("$[0].toolName").doesNotExist());
    }
}
