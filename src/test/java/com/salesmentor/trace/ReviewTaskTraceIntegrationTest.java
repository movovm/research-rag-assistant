package com.salesmentor.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmentor.agent.application.SalesReviewAgent;
import com.salesmentor.agent.model.ReviewInput;
import com.salesmentor.agent.report.ReviewReport;
import com.salesmentor.review.application.ReviewTaskApplicationService;
import com.salesmentor.review.domain.ReviewTask;
import com.salesmentor.review.domain.ReviewTaskRepository;
import com.salesmentor.trace.application.ReviewTaskTraceRecorder;
import com.salesmentor.trace.domain.AgentTrace;
import com.salesmentor.trace.domain.AgentTraceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class ReviewTaskTraceIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("salesmentor").withUsername("salesmentor").withPassword("salesmentor");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private ReviewTaskRepository tasks;
    @Autowired private AgentTraceRepository traces;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void successAppendsOnlySafeOrderedTaskTraces() {
        ReviewTask task = tasks.save(task("trace-success-" + System.nanoTime()));
        SalesReviewAgent agent = mock(SalesReviewAgent.class);
        when(agent.review(any())).thenReturn(report());

        ReviewTask result = service(agent, objectMapper, new ReviewTaskTraceRecorder(traces)).execute(task.id());

        assertThat(result.status()).isEqualTo(ReviewTask.Status.SUCCEEDED);
        assertThat(traces.findByTaskId(task.id())).satisfiesExactly(
                trace(1, "review task claimed", AgentTrace.Status.STARTED, null),
                trace(2, "review agent completed", AgentTrace.Status.SUCCEEDED, null),
                trace(3, "review task succeeded", AgentTrace.Status.SUCCEEDED, null));

        AgentTrace persisted = traces.findByTaskId(task.id()).get(0);
        assertThatThrownBy(() -> traces.append(new AgentTrace(persisted.id(), persisted.taskId(), persisted.stepNo(),
                persisted.stepType(), persisted.toolName(), persisted.inputJson(), persisted.outputSummary(),
                persisted.evidenceIds(), persisted.durationMs(), persisted.status(), persisted.errorCode(),
                persisted.createdAt()))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void agentAndSerializationFailuresKeepOnlySafeTraceFacts() throws Exception {
        ReviewTask agentTask = tasks.save(task("trace-agent-failure-" + System.nanoTime()));
        SalesReviewAgent failingAgent = mock(SalesReviewAgent.class);
        when(failingAgent.review(any())).thenThrow(new IllegalStateException("customer secret and quote"));

        assertThat(service(failingAgent, objectMapper, new ReviewTaskTraceRecorder(traces)).execute(agentTask.id()).status())
                .isEqualTo(ReviewTask.Status.FAILED);
        assertThat(traces.findByTaskId(agentTask.id())).satisfiesExactly(
                trace(1, "review task claimed", AgentTrace.Status.STARTED, null),
                trace(3, "review task failed", AgentTrace.Status.FAILED, "AGENT_FAILED"));

        ReviewTask serializationTask = tasks.save(task("trace-serialization-failure-" + System.nanoTime()));
        SalesReviewAgent successfulAgent = mock(SalesReviewAgent.class);
        when(successfulAgent.review(any())).thenReturn(report());
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("secret") {});

        assertThat(service(successfulAgent, failingMapper, new ReviewTaskTraceRecorder(traces)).execute(serializationTask.id()).status())
                .isEqualTo(ReviewTask.Status.FAILED);
        assertThat(traces.findByTaskId(serializationTask.id())).satisfiesExactly(
                trace(1, "review task claimed", AgentTrace.Status.STARTED, null),
                trace(2, "review agent completed", AgentTrace.Status.SUCCEEDED, null),
                trace(3, "review task failed", AgentTrace.Status.FAILED, "REPORT_SERIALIZATION_FAILED"));
    }

    @Test
    void tracePersistenceFailureDoesNotChangeTaskFactAndTerminalTasksDoNotAppend() {
        ReviewTask task = tasks.save(task("trace-recorder-failure-" + System.nanoTime()));
        AgentTraceRepository unavailable = mock(AgentTraceRepository.class);
        doThrow(new IllegalStateException("trace failure")).when(unavailable).append(any());
        SalesReviewAgent agent = mock(SalesReviewAgent.class);
        when(agent.review(any())).thenReturn(report());
        ReviewTaskApplicationService service = service(agent, objectMapper, new ReviewTaskTraceRecorder(unavailable));

        assertThat(service.execute(task.id()).status()).isEqualTo(ReviewTask.Status.SUCCEEDED);
        assertThat(traces.findByTaskId(task.id())).isEmpty();
        assertThat(service.execute(task.id()).status()).isEqualTo(ReviewTask.Status.SUCCEEDED);
        verify(agent, times(1)).review(any(ReviewInput.class));
    }

    private ReviewTaskApplicationService service(SalesReviewAgent agent, ObjectMapper mapper,
                                                 ReviewTaskTraceRecorder recorder) {
        return new ReviewTaskApplicationService(tasks, agent, mapper, recorder);
    }

    private ReviewTask task(String requestId) {
        LocalDateTime now = LocalDateTime.now();
        return new ReviewTask(null, requestId, null, null, "industry", null, "buyer", "private input quote",
                "private review goal", ReviewTask.Status.PENDING, null, null, null, 0, null, null,
                null, null, now, now);
    }

    private ReviewReport report() {
        return new ReviewReport(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private Consumer<AgentTrace> trace(int stepNo, String summary, AgentTrace.Status status, String errorCode) {
        return value -> {
            assertThat(value.stepNo()).isEqualTo(stepNo);
            assertThat(value.stepType()).isEqualTo(AgentTrace.StepType.TASK);
            assertThat(value.status()).isEqualTo(status);
            assertThat(value.outputSummary()).isEqualTo(summary);
            assertThat(value.errorCode()).isEqualTo(errorCode);
            assertThat(value.toolName()).isNull();
            assertThat(value.inputJson()).isNull();
            assertThat(value.evidenceIds()).isNull();
            assertThat(value.durationMs()).isGreaterThanOrEqualTo(0);
        };
    }
}
