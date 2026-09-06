package com.salesmentor.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmentor.agent.application.SalesReviewAgent;
import com.salesmentor.agent.report.ReviewReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReviewControllerSseIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("salesmentor").withUsername("salesmentor").withPassword("salesmentor");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SalesReviewAgent agent;

    @Test
    void clientDisconnectDoesNotStopTaskAndReconnectGetsTerminalSnapshot() throws Exception {
        CountDownLatch agentStarted = new CountDownLatch(1);
        CountDownLatch releaseAgent = new CountDownLatch(1);
        when(agent.review(any())).thenAnswer(invocation -> {
            agentStarted.countDown();
            assertThat(releaseAgent.await(10, TimeUnit.SECONDS)).isTrue();
            return new ReviewReport(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        });

        String requestId = "sse-disconnect-" + System.nanoTime();
        String body = "{\"requestId\":\"" + requestId
                + "\",\"industry\":\"tech\",\"conversationContent\":\"price objection\","
                + "\"reviewGoal\":\"review\"}";
        ResponseEntity<String> created = rest.postForEntity("/api/reviews",
                new HttpEntity<>(body, jsonHeaders()), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long taskId = objectMapper.readTree(created.getBody()).get("taskId").asLong();
        assertThat(agentStarted.await(10, TimeUnit.SECONDS)).isTrue();

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest eventsRequest = HttpRequest.newBuilder(eventsUri(taskId))
                .timeout(Duration.ofSeconds(10)).header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE).GET().build();
        HttpResponse<InputStream> firstConnection = client.send(eventsRequest, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(firstConnection.statusCode()).isEqualTo(200);
        try (InputStream stream = firstConnection.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String event = readEvent(reader, 5_000);
            assertThat(event).contains("event:task-status").contains("\"taskId\":" + taskId);
            assertThat(event).contains("\"status\":\"RUNNING\"");
        }

        releaseAgent.countDown();
        ResponseEntity<String> completed = awaitTerminal(taskId, 10_000);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode completedJson = objectMapper.readTree(completed.getBody());
        assertThat(completedJson.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(completedJson.get("report").isObject()).isTrue();
        verify(agent, times(1)).review(any());

        HttpResponse<InputStream> terminalConnection = client.send(eventsRequest,
                HttpResponse.BodyHandlers.ofInputStream());
        assertThat(terminalConnection.statusCode()).isEqualTo(200);
        try (InputStream stream = terminalConnection.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String event = readEvent(reader, 5_000);
            assertThat(event).contains("event:task-status").contains("\"status\":\"SUCCEEDED\"");
            assertThat(event).contains("\"taskId\":" + taskId);
            awaitEndOfStream(reader, 5_000);
        } finally {
            releaseAgent.countDown();
        }
    }

    private ResponseEntity<String> awaitTerminal(long taskId, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        ResponseEntity<String> response;
        do {
            response = rest.getForEntity("/api/reviews/{id}", String.class, taskId);
            if (response.getStatusCode().is2xxSuccessful()
                    && response.getBody() != null
                    && (response.getBody().contains("\"SUCCEEDED\"") || response.getBody().contains("\"FAILED\""))) {
                return response;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        return response;
    }

    private URI eventsUri(long taskId) {
        return URI.create("http://localhost:" + port + "/api/reviews/" + taskId + "/events");
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String readEvent(BufferedReader reader, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        StringBuilder event = new StringBuilder();
        while (System.nanoTime() < deadline) {
            if (!reader.ready()) {
                Thread.sleep(10);
                continue;
            }
            String line = reader.readLine();
            if (line == null) break;
            if (line.isEmpty()) return event.toString();
            event.append(line).append('\n');
        }
        throw new AssertionError("timed out waiting for SSE event");
    }

    private void awaitEndOfStream(BufferedReader reader, long timeoutMillis) throws Exception {
        FutureTask<String> eofRead = new FutureTask<>(reader::readLine);
        Thread readerThread = new Thread(eofRead, "sse-test-eof-reader");
        readerThread.start();
        try {
            assertThat(eofRead.get(timeoutMillis, TimeUnit.MILLISECONDS)).isNull();
        } finally {
            eofRead.cancel(true);
        }
    }
}
