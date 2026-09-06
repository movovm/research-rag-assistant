package com.salesmentor.review;

import com.salesmentor.review.application.ReviewTaskTimeoutService;
import com.salesmentor.review.domain.ReviewTask;
import com.salesmentor.review.domain.ReviewTaskRepository;
import com.salesmentor.trace.application.ReviewTaskTraceRecorder;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReviewTaskTimeoutServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 6, 12, 0);

    @Test
    void closesExpiredRunningTaskAndRecordsOnlyAfterCasSuccess() {
        ReviewTaskRepository repository = mock(ReviewTaskRepository.class);
        ReviewTaskTraceRecorder traces = mock(ReviewTaskTraceRecorder.class);
        ReviewTask task = task(7L, 3, NOW.minusSeconds(121));
        when(repository.findExpiredRunning(NOW.minusSeconds(120), 100)).thenReturn(List.of(task));
        when(repository.timeout(7L, 3, NOW.minusSeconds(120))).thenReturn(true);
        ReviewTaskTimeoutService service = service(repository, traces, 120);

        assertThat(service.scanOnce(NOW)).isEqualTo(1);
        verify(traces).failed(7L, 121_000L, "REVIEW_EXECUTION_TIMEOUT");
    }

    @Test
    void casLossDoesNotRecordTimeoutTrace() {
        ReviewTaskRepository repository = mock(ReviewTaskRepository.class);
        ReviewTaskTraceRecorder traces = mock(ReviewTaskTraceRecorder.class);
        ReviewTask task = task(8L, 1, NOW.minusSeconds(300));
        when(repository.findExpiredRunning(any(), eq(100))).thenReturn(List.of(task));
        when(repository.timeout(eq(8L), eq(1L), any())).thenReturn(false);
        assertThat(service(repository, traces, 120).scanOnce(NOW)).isZero();
        verifyNoInteractions(traces);
    }

    @Test
    void concurrentTimeoutCasHasExactlyOneWinner() throws Exception {
        ReviewTaskRepository repository = mock(ReviewTaskRepository.class);
        ReviewTask task = task(9L, 2, NOW.minusSeconds(300));
        when(repository.findExpiredRunning(any(), eq(100))).thenReturn(List.of(task));
        // The repository CAS winner is modeled by an atomic gate shared by both scans.
        java.util.concurrent.atomic.AtomicBoolean winner = new java.util.concurrent.atomic.AtomicBoolean();
        when(repository.findExpiredRunning(any(), eq(100))).thenReturn(List.of(task));
        when(repository.timeout(eq(9L), eq(2L), any())).thenAnswer(invocation -> {
            return winner.compareAndSet(false, true);
        });
        ReviewTaskTimeoutService service = service(repository, mock(ReviewTaskTraceRecorder.class), 120);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            var first = callers.submit(() -> service.scanOnce(NOW));
            var second = callers.submit(() -> service.scanOnce(NOW));
            assertThat(first.get(2, TimeUnit.SECONDS) + second.get(2, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void invalidConfigurationIsRejected() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    new ReviewTaskTimeoutService(mock(ReviewTaskRepository.class), mock(ReviewTaskTraceRecorder.class),
                    scheduler, Clock.systemUTC(), 0L, 5L, 100))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            scheduler.shutdownNow();
        }
    }

    private ReviewTaskTimeoutService service(ReviewTaskRepository repository, ReviewTaskTraceRecorder traces,
                                             long timeoutSeconds) {
        return new ReviewTaskTimeoutService(repository, traces, new ScheduledThreadPoolExecutor(1),
                Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC), timeoutSeconds, 5L, 100);
    }

    private ReviewTask task(Long id, long version, LocalDateTime startedAt) {
        return new ReviewTask(id, "timeout-" + id, null, null, "tech", null, "buyer", "conversation",
                "goal", ReviewTask.Status.RUNNING, null, null, null, version, null, null,
                startedAt, null, startedAt, startedAt);
    }
}
