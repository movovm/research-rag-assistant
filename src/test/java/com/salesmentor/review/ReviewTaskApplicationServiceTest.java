package com.salesmentor.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmentor.agent.application.SalesReviewAgent;
import com.salesmentor.agent.model.ReviewInput;
import com.salesmentor.agent.report.ReviewReport;
import com.salesmentor.review.application.ReviewTaskApplicationService;
import com.salesmentor.review.domain.ReviewTask;
import com.salesmentor.review.domain.ReviewTaskRepository;
import com.salesmentor.trace.application.ReviewTaskTraceRecorder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReviewTaskApplicationServiceTest {
    @Test
    void createsPendingTaskAndMakesIdenticalRequestIdempotent() {
        ReviewTaskRepository repository = mock(ReviewTaskRepository.class);
        ReviewInput input = input("request-1", "content");
        ReviewTask saved = task(1L, input, ReviewTask.Status.PENDING, 0);
        when(repository.findByRequestId(input.requestId())).thenReturn(Optional.empty(), Optional.of(saved));
        when(repository.save(any())).thenReturn(saved);
        ReviewTaskApplicationService service = service(repository, mock(SalesReviewAgent.class), new ObjectMapper());

        assertThat(service.create(input)).isSameAs(saved);
        assertThat(service.create(input)).isSameAs(saved);
        verify(repository, times(1)).save(any());
    }

    @Test
    void rejectsSameRequestIdWithDifferentInput() {
        ReviewTaskRepository repository = mock(ReviewTaskRepository.class);
        ReviewInput original = input("request-2", "content");
        when(repository.findByRequestId(original.requestId()))
                .thenReturn(Optional.of(task(2L, original, ReviewTask.Status.PENDING, 0)));
        ReviewTaskApplicationService service = service(repository, mock(SalesReviewAgent.class), new ObjectMapper());

        assertThatThrownBy(() -> service.create(input("request-2", "different")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void executesTaskThroughStrictSuccessCas() {
        ReviewTaskRepository repository = mock(ReviewTaskRepository.class);
        SalesReviewAgent agent = mock(SalesReviewAgent.class);
        ReviewInput input = input("request-3", "price objection");
        ReviewTask pending = task(3L, input, ReviewTask.Status.PENDING, 0);
        ReviewTask succeeded = task(3L, input, ReviewTask.Status.SUCCEEDED, 2);
        when(repository.findById(3L)).thenReturn(Optional.of(pending), Optional.of(succeeded));
        when(repository.start(3L, 0)).thenReturn(true);
        when(repository.succeed(eq(3L), eq(1L), anyString())).thenReturn(true);
        when(agent.review(input)).thenReturn(report());

        ReviewTask result = service(repository, agent, new ObjectMapper()).execute(3L);

        assertThat(result).isSameAs(succeeded);
        verify(repository).succeed(eq(3L), eq(1L), contains("currentObservations"));
        verify(repository, never()).fail(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void agentFailureUsesSafeFailureFields() {
        ReviewTaskRepository repository = mock(ReviewTaskRepository.class);
        SalesReviewAgent agent = mock(SalesReviewAgent.class);
        ReviewInput input = input("request-4", "content");
        ReviewTask pending = task(4L, input, ReviewTask.Status.PENDING, 0);
        ReviewTask failed = task(4L, input, ReviewTask.Status.FAILED, 2);
        when(repository.findById(4L)).thenReturn(Optional.of(pending), Optional.of(failed));
        when(repository.start(4L, 0)).thenReturn(true);
        when(agent.review(input)).thenThrow(new IllegalStateException("customer secret must not persist"));

        ReviewTask result = service(repository, agent, new ObjectMapper()).execute(4L);

        assertThat(result).isSameAs(failed);
        verify(repository).fail(4L, 1L, "AGENT_FAILED", "review agent failed");
    }

    @Test
    void serializationFailureUsesFixedCodeAndStartCasFailureDoesNotCallAgent() throws Exception {
        ReviewTaskRepository repository = mock(ReviewTaskRepository.class);
        SalesReviewAgent agent = mock(SalesReviewAgent.class);
        ReviewInput input = input("request-5", "content");
        ReviewTask pending = task(5L, input, ReviewTask.Status.PENDING, 0);
        ReviewTask running = task(5L, input, ReviewTask.Status.RUNNING, 1);
        when(repository.findById(5L)).thenReturn(Optional.of(pending), Optional.of(running));
        when(repository.start(5L, 0)).thenReturn(true);
        when(agent.review(input)).thenReturn(report());
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("secret") {});
        service(repository, agent, mapper).execute(5L);
        verify(repository).fail(5L, 1L, "REPORT_SERIALIZATION_FAILED", "review report serialization failed");

        ReviewTaskRepository casRepository = mock(ReviewTaskRepository.class);
        when(casRepository.findById(5L)).thenReturn(Optional.of(pending), Optional.of(running));
        when(casRepository.start(5L, 0)).thenReturn(false);
        SalesReviewAgent untouched = mock(SalesReviewAgent.class);
        assertThat(service(casRepository, untouched, new ObjectMapper()).execute(5L)).isSameAs(running);
        verifyNoInteractions(untouched);
    }

    @Test
    void doesNotRunAgentForRunningOrTerminalTask() {
        ReviewTaskRepository repository = mock(ReviewTaskRepository.class);
        ReviewInput input = input("request-6", "content");
        ReviewTask running = task(6L, input, ReviewTask.Status.RUNNING, 1);
        when(repository.findById(6L)).thenReturn(Optional.of(running));
        SalesReviewAgent agent = mock(SalesReviewAgent.class);
        assertThat(service(repository, agent, new ObjectMapper()).execute(6L)).isSameAs(running);
        verifyNoInteractions(agent);
    }

    @Test
    void lateAgentResultCannotOverwriteTimeoutFailure() throws Exception {
        ReviewTaskRepository repository = mock(ReviewTaskRepository.class);
        SalesReviewAgent agent = mock(SalesReviewAgent.class);
        ReviewInput input = input("request-timeout", "sensitive conversation");
        ReviewTask pending = task(7L, input, ReviewTask.Status.PENDING, 0);
        ReviewTask failed = new ReviewTask(7L, input.requestId(), null, null, input.industry(), input.salesStage(),
                input.customerRole(), input.conversationContent(), input.reviewGoal(), ReviewTask.Status.FAILED,
                null, null, null, 2, "REVIEW_EXECUTION_TIMEOUT", "review execution deadline exceeded",
                null, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(repository.findById(7L)).thenReturn(Optional.of(pending), Optional.of(failed), Optional.of(failed));
        when(repository.start(7L, 0)).thenReturn(true);
        when(repository.succeed(eq(7L), eq(1L), anyString())).thenReturn(false);
        when(agent.review(input)).thenAnswer(invocation -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return report();
        });
        ReviewTaskTraceRecorder traces = mock(ReviewTaskTraceRecorder.class);
        ReviewTaskApplicationService service = new ReviewTaskApplicationService(repository, agent, new ObjectMapper(), traces);
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var result = worker.submit(() -> service.execute(7L));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(result.get(2, TimeUnit.SECONDS)).isSameAs(failed);
            verify(agent, times(1)).review(input);
            verify(repository, never()).fail(anyLong(), anyLong(), eq("AGENT_FAILED"), anyString());
            verify(repository).succeed(eq(7L), eq(1L), anyString());
            verify(traces, never()).succeeded(eq(7L), anyLong());
            assertThat(failed.reportJson()).isNull();
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    private ReviewTaskApplicationService service(ReviewTaskRepository repository, SalesReviewAgent agent,
                                                  ObjectMapper mapper) {
        return new ReviewTaskApplicationService(repository, agent, mapper, mock(ReviewTaskTraceRecorder.class));
    }

    private ReviewInput input(String requestId, String content) {
        return new ReviewInput(requestId, "industry", null, "buyer", content, "review goal");
    }

    private ReviewTask task(Long id, ReviewInput input, ReviewTask.Status status, long version) {
        LocalDateTime now = LocalDateTime.now();
        return new ReviewTask(id, input.requestId(), null, null, input.industry(), input.salesStage(),
                input.customerRole(), input.conversationContent(), input.reviewGoal(), status, null, null,
                null, version, null, null, null, null, now, now);
    }

    private ReviewReport report() {
        return new ReviewReport(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
