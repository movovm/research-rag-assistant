package com.salesmentor.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmentor.agent.application.SalesReviewAgent;
import com.salesmentor.agent.report.ReviewReport;
import com.salesmentor.review.application.ReviewTaskApplicationService;
import com.salesmentor.review.application.ReviewTaskSubmissionService;
import com.salesmentor.review.domain.ReviewTask;
import com.salesmentor.review.domain.ReviewTaskRepository;
import com.salesmentor.trace.application.ReviewTaskTraceRecorder;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class ReviewTaskSubmissionServiceIntegrationTest {
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
    @Autowired private ObjectMapper mapper;

    @Test
    void duplicateSubmissionsUseStrictCasAndInvokeAgentOnce() throws Exception {
        ReviewTask saved = tasks.save(task("submit-race-" + System.nanoTime()));
        SalesReviewAgent agent = mock(SalesReviewAgent.class);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(1);
        when(agent.review(any())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            completed.countDown();
            return new ReviewReport(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        });
        ReviewTaskApplicationService application = new ReviewTaskApplicationService(tasks, agent, mapper,
                new ReviewTaskTraceRecorder(traces));
        java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        Executor dispatch = workers::execute;
        ReviewTaskSubmissionService submission = new ReviewTaskSubmissionService(application, dispatch);
        assertThat(submission.submit(saved.id()).status()).isEqualTo(ReviewTaskSubmissionService.ReviewTaskSubmission.Status.ACCEPTED);
        assertThat(submission.submit(saved.id()).status()).isEqualTo(ReviewTaskSubmissionService.ReviewTaskSubmission.Status.ACCEPTED);
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        workers.shutdown();
        workers.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(calls).hasValue(1);
        assertThat(tasks.findById(saved.id())).get().extracting(ReviewTask::status, ReviewTask::version)
                .containsExactly(ReviewTask.Status.SUCCEEDED, 2L);
    }

    private ReviewTask task(String requestId) {
        LocalDateTime now = LocalDateTime.now();
        return new ReviewTask(null, requestId, null, null, "industry", null, "buyer", "conversation",
                "review goal", ReviewTask.Status.PENDING, null, null, null, 0, null, null, null, null, now, now);
    }
}
