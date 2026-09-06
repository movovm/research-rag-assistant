package com.salesmentor.review;

import com.salesmentor.review.application.ReviewTaskTimeoutService;
import com.salesmentor.review.domain.ReviewTask;
import com.salesmentor.review.domain.ReviewTaskRepository;
import com.salesmentor.trace.application.ReviewTaskTraceRecorder;
import com.salesmentor.trace.domain.AgentTraceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ReviewTaskTimeoutIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("salesmentor").withUsername("salesmentor").withPassword("salesmentor");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private ReviewTaskRepository repository;
    @Autowired private AgentTraceRepository traceRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void expiredRunningTaskIsClosedFromDatabaseWithoutInMemoryExecutionRecord() {
        ReviewTask task = repository.save(new ReviewTask(null, "timeout-it-" + System.nanoTime(), null, null,
                "tech", null, "buyer", "conversation", "goal", ReviewTask.Status.PENDING,
                null, null, null, 0, null, null, null, null, LocalDateTime.now(), LocalDateTime.now()));
        assertThat(repository.start(task.id(), 0)).isTrue();
        LocalDateTime started = LocalDateTime.now().minusMinutes(3);
        jdbc.update("UPDATE sm_review_task SET started_at = ?, updated_at = ? WHERE id = ?", started, started, task.id());

        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
        try {
            ReviewTaskTimeoutService timeout = new ReviewTaskTimeoutService(repository,
                    new ReviewTaskTraceRecorder(traceRepository), scheduler,
                    Clock.systemDefaultZone(), 120L, 5L, 100);
            assertThat(timeout.scanOnce(LocalDateTime.now())).isEqualTo(1);
            ReviewTask failed = repository.findById(task.id()).orElseThrow();
            assertThat(failed.status()).isEqualTo(ReviewTask.Status.FAILED);
            assertThat(failed.version()).isEqualTo(2);
            assertThat(failed.failureCode()).isEqualTo("REVIEW_EXECUTION_TIMEOUT");
            assertThat(failed.failureReason()).isEqualTo("review execution deadline exceeded");
            assertThat(repository.succeed(task.id(), 1, "{\"late\":true}")).isFalse();
            assertThat(traceRepository.findByTaskId(task.id())).hasSize(1)
                    .first().extracting(com.salesmentor.trace.domain.AgentTrace::errorCode)
                    .isEqualTo("REVIEW_EXECUTION_TIMEOUT");
        } finally {
            scheduler.shutdownNow();
        }
    }
}
