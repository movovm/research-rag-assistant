package com.salesmentor.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmentor.agent.application.SalesReviewAgent;
import com.salesmentor.agent.model.ReviewInput;
import com.salesmentor.review.application.ReviewTaskApplicationService;
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

import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
@Testcontainers
class ReviewTaskApplicationServiceIntegrationTest {
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

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgentTraceRepository traces;

    @Test
    void concurrentCreateWithSameRequestIdKeepsOneTask() throws Exception {
        ReviewTaskApplicationService service = new ReviewTaskApplicationService(repository,
                mock(SalesReviewAgent.class), objectMapper, new ReviewTaskTraceRecorder(traces));
        ReviewInput input = new ReviewInput("same-" + System.nanoTime(), "industry", null,
                "buyer", "conversation", "review goal");
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(3);
        try {
            Callable<ReviewTask> create = () -> { barrier.await(); return service.create(input); };
            Future<ReviewTask> first = callers.submit(create);
            Future<ReviewTask> second = callers.submit(create);
            barrier.await();
            List<ReviewTask> tasks = List.of(first.get(3, TimeUnit.SECONDS), second.get(3, TimeUnit.SECONDS));
            assertThat(tasks).extracting(ReviewTask::id).containsOnly(tasks.get(0).id());
            assertThat(repository.findByRequestId(input.requestId())).get()
                    .extracting(ReviewTask::status, ReviewTask::version)
                    .containsExactly(ReviewTask.Status.PENDING, 0L);
        } finally {
            callers.shutdownNow();
        }
    }
}
