package com.salesmentor.review.application;

import com.salesmentor.review.domain.ReviewTask;
import com.salesmentor.review.domain.ReviewTaskRepository;
import com.salesmentor.trace.application.ReviewTaskTraceRecorder;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReviewTaskTimeoutResilienceTest {
    @Test
    void databaseFailureDoesNotStopTheNextScan() {
        ReviewTaskRepository repository = mock(ReviewTaskRepository.class);
        ReviewTaskTraceRecorder traces = mock(ReviewTaskTraceRecorder.class);
        ReviewTask task = new ReviewTask(1L, "resilience", null, null, "tech", null, "buyer", "conversation",
                "goal", ReviewTask.Status.RUNNING, null, null, null, 1, null, null,
                LocalDateTime.of(2026, 9, 6, 11, 0), null, null, null);
        AtomicInteger calls = new AtomicInteger();
        when(repository.findExpiredRunning(any(), eq(100))).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) throw new IllegalStateException("database unavailable");
            return List.of(task);
        });
        when(repository.timeout(anyLong(), anyLong(), any())).thenReturn(true);
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
        try {
            ReviewTaskTimeoutService service = new ReviewTaskTimeoutService(repository, traces, scheduler,
                    Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC), 120L, 5L, 100);
            service.scanSafely();
            service.scanSafely();
            verify(repository, times(2)).findExpiredRunning(any(), eq(100));
            verify(repository).timeout(eq(1L), eq(1L), any());
            verify(traces).failed(eq(1L), anyLong(), eq("REVIEW_EXECUTION_TIMEOUT"));
        } finally {
            scheduler.shutdownNow();
        }
    }
}
