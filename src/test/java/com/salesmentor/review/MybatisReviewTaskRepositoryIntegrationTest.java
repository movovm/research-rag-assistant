package com.salesmentor.review;

import com.salesmentor.review.domain.ReviewTask;
import com.salesmentor.review.domain.ReviewTaskRepository;
import com.salesmentor.salescase.domain.SalesCase;
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
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class MybatisReviewTaskRepositoryIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("salesmentor").withUsername("salesmentor").withPassword("salesmentor");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private ReviewTaskRepository repository;

    @Test
    void persistsStrictLifecycleAndVersionedCas() {
        ReviewTask task = repository.save(task("persist-" + System.nanoTime()));
        assertThat(repository.findById(task.id())).get()
                .extracting(ReviewTask::status, ReviewTask::version).containsExactly(ReviewTask.Status.PENDING, 0L);
        assertThat(repository.start(task.id(), 0)).isTrue();
        assertThat(repository.start(task.id(), 0)).isFalse();
        assertThat(repository.succeed(task.id(), 1, "{\"status\":\"ok\"}")).isTrue();
        assertThat(repository.succeed(task.id(), 1, "{\"status\":\"again\"}")).isFalse();
        assertThat(repository.findById(task.id())).get()
                .extracting(ReviewTask::status, ReviewTask::version)
                .containsExactly(ReviewTask.Status.SUCCEEDED, 2L);
        assertThat(repository.findById(task.id()).orElseThrow().reportJson().replaceAll("\\s+", ""))
                .isEqualTo("{\"status\":\"ok\"}");
    }

    @Test
    void failureCasStoresControlledFieldsAndRejectsInvalidTransitions() {
        ReviewTask task = repository.save(task("fail-" + System.nanoTime()));
        assertThat(repository.succeed(task.id(), 0, "snapshot")).isFalse();
        assertThat(repository.start(task.id(), 0)).isTrue();
        assertThat(repository.fail(task.id(), 0, "BAD", "stale version")).isFalse();
        assertThat(repository.fail(task.id(), 1, "TOOL_FAILED", "bounded failure")).isTrue();
        assertThat(repository.fail(task.id(), 2, "AGAIN", "terminal")).isFalse();
        assertThat(repository.findById(task.id())).get()
                .extracting(ReviewTask::status, ReviewTask::version, ReviewTask::failureCode, ReviewTask::failureReason)
                .containsExactly(ReviewTask.Status.FAILED, 2L, "TOOL_FAILED", "bounded failure");
    }

    @Test
    void concurrentStartHasExactlyOneWinner() throws Exception {
        ReviewTask task = repository.save(task("concurrent-" + System.nanoTime()));
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(3);
        try {
            Callable<Boolean> attempt = () -> { barrier.await(); return repository.start(task.id(), 0); };
            Future<Boolean> first = callers.submit(attempt);
            Future<Boolean> second = callers.submit(attempt);
            barrier.await();
            assertThat(List.of(first.get(3, TimeUnit.SECONDS), second.get(3, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void rejectsBlankSnapshotAndFailureFieldsBeforeSql() {
        ReviewTask task = repository.save(task("invalid-" + System.nanoTime()));
        assertThat(repository.start(task.id(), 0)).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.succeed(task.id(), 1, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.fail(task.id(), 1, " ", "reason"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findById(task.id())).get()
                .extracting(ReviewTask::status, ReviewTask::version).containsExactly(ReviewTask.Status.RUNNING, 1L);
    }

    @Test
    void timeoutCannotOverwriteAlreadySucceededReport() {
        ReviewTask task = repository.save(task("timeout-after-success-" + System.nanoTime()));
        assertThat(repository.start(task.id(), 0)).isTrue();
        assertThat(repository.succeed(task.id(), 1, "{\"ok\":true}")).isTrue();
        assertThat(repository.timeout(task.id(), 1, LocalDateTime.now().plusMinutes(1))).isFalse();
        assertThat(repository.findById(task.id())).get()
                .extracting(ReviewTask::status, ReviewTask::reportJson)
                .containsExactly(ReviewTask.Status.SUCCEEDED, "{\"ok\": true}");
    }

    private ReviewTask task(String requestId) {
        LocalDateTime now = LocalDateTime.now();
        return new ReviewTask(null, requestId, null, null, "MANUFACTURING", SalesCase.SalesStage.NEGOTIATION,
                "BUYER", "conversation", "review goal", ReviewTask.Status.PENDING, null, null, null,
                0, null, null, null, null, now, now);
    }
}
