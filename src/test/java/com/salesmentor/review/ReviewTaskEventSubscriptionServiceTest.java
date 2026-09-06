package com.salesmentor.review;

import com.salesmentor.review.application.ReviewTaskApplicationService;
import com.salesmentor.review.application.ReviewTaskEventSubscriptionService;
import com.salesmentor.review.application.ReviewSubscriptionCapacityException;
import com.salesmentor.review.domain.ReviewTask;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReviewTaskEventSubscriptionServiceTest {
    @Test
    void subscribesWithoutCallingExecutionChainAndPollsOnlyAfterStateChanges() {
        ReviewTaskApplicationService tasks = mock(ReviewTaskApplicationService.class);
        ReviewTask pending = task(1L, ReviewTask.Status.PENDING, 0);
        ReviewTask running = task(1L, ReviewTask.Status.RUNNING, 1);
        when(tasks.find(1L)).thenReturn(pending, running);
        CapturingScheduler scheduler = new CapturingScheduler();
        ReviewTaskEventSubscriptionService service = new ReviewTaskEventSubscriptionService(tasks, scheduler);

        SseEmitter emitter = service.subscribe(1L);
        assertThat(emitter).isNotNull();
        scheduler.command.run();
        verify(tasks, times(2)).find(1L);
        verify(tasks, never()).create(any());
        verify(tasks, never()).execute(anyLong());
    }

    @Test
    void terminalSnapshotDoesNotRegisterPollingAndCapacityIsReleasedOnCompletion() {
        ReviewTaskApplicationService tasks = mock(ReviewTaskApplicationService.class);
        CapturingScheduler scheduler = new CapturingScheduler();
        ReviewTaskEventSubscriptionService service = new ReviewTaskEventSubscriptionService(tasks, scheduler);
        when(tasks.find(1L)).thenReturn(task(1L, ReviewTask.Status.SUCCEEDED, 2));
        service.subscribe(1L);
        assertThat(scheduler.command).isNull();
        when(tasks.find(anyLong())).thenAnswer(invocation -> task(invocation.getArgument(0), ReviewTask.Status.PENDING, 0));
        List<SseEmitter> emitters = new java.util.ArrayList<>();
        for (long id = 2; id <= 64; id++) emitters.add(service.subscribe(id));
        emitters.add(service.subscribe(65L));
        assertThatThrownBy(() -> service.subscribe(66L)).isInstanceOf(ReviewSubscriptionCapacityException.class);
    }

    private ReviewTask task(Long id, ReviewTask.Status status, long version) {
        return new ReviewTask(id, "req-" + id, null, null, null, null, "buyer", "conversation",
                "goal", status, null, null, null, version, null, null, null, null, null, null);
    }

    private static class CapturingScheduler extends ScheduledThreadPoolExecutor {
        private Runnable command;
        CapturingScheduler() { super(1); }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
                                                                     long delay, TimeUnit unit) {
            this.command = command;
            return mock(ScheduledFuture.class);
        }
    }
}
