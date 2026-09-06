package com.salesmentor.review.application;

import com.salesmentor.review.domain.ReviewTask;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ReviewTaskEventSubscriptionService {
    private static final int MAX_ACTIVE = 64;
    private static final long POLL_MILLIS = 1_000L;
    private static final long CONNECTION_MILLIS = 60_000L;
    private final ReviewTaskApplicationService tasks;
    private final ScheduledThreadPoolExecutor scheduler;
    private final AtomicInteger active = new AtomicInteger();

    public ReviewTaskEventSubscriptionService(ReviewTaskApplicationService tasks,
                                               @Qualifier("reviewEventsScheduler") ScheduledThreadPoolExecutor scheduler) {
        if (tasks == null || scheduler == null) throw new IllegalArgumentException("event subscription dependencies are required");
        this.tasks = tasks;
        this.scheduler = scheduler;
    }

    public SseEmitter subscribe(Long taskId) {
        ReviewTask initial = tasks.find(taskId);
        acquireSlot();
        Subscription subscription = new Subscription(taskId, new SseEmitter(CONNECTION_MILLIS));
        try {
            subscription.emitter.onCompletion(subscription::cleanup);
            subscription.emitter.onTimeout(() -> {
                subscription.sendErrorAndCleanup();
            });
            subscription.emitter.onError(error -> subscription.cleanup());
            subscription.publishIfChanged(initial);
            if (subscription.terminal || subscription.cleaned.get()) {
                subscription.cleanup();
            } else {
                subscription.future = scheduler.scheduleWithFixedDelay(subscription::pollSafely,
                        POLL_MILLIS, POLL_MILLIS, TimeUnit.MILLISECONDS);
            }
            return subscription.emitter;
        } catch (RuntimeException ex) {
            subscription.cleanup();
            throw ex;
        }
    }

    private void acquireSlot() {
        while (true) {
            int current = active.get();
            if (current >= MAX_ACTIVE || !active.compareAndSet(current, current + 1)) {
                if (current >= MAX_ACTIVE) throw new ReviewSubscriptionCapacityException();
                continue;
            }
            return;
        }
    }

    private final class Subscription {
        private final Long taskId;
        private final SseEmitter emitter;
        private final AtomicBoolean cleaned = new AtomicBoolean();
        private final Object sendLock = new Object();
        private volatile long lastVersion = Long.MIN_VALUE;
        private volatile ReviewTask.Status lastStatus;
        private volatile boolean terminal;
        private volatile ScheduledFuture<?> future;

        private Subscription(Long taskId, SseEmitter emitter) {
            this.taskId = taskId;
            this.emitter = emitter;
        }

        private void pollSafely() {
            if (cleaned.get()) return;
            try {
                publishIfChanged(tasks.find(taskId));
                if (terminal) cleanup();
            } catch (RuntimeException ex) {
                sendErrorAndCleanup();
            }
        }

        private void publishIfChanged(ReviewTask task) {
            if (cleaned.get()) return;
            if (task.version() == lastVersion && task.status() == lastStatus) return;
            ReviewTaskStatusEvent event = ReviewTaskStatusEvent.from(task);
            synchronized (sendLock) {
                if (cleaned.get()) return;
                try {
                    emitter.send(SseEmitter.event().id(Long.toString(task.version())).name("task-status")
                            .data(event, MediaType.APPLICATION_JSON));
                    lastVersion = task.version();
                    lastStatus = task.status();
                    terminal = task.status() == ReviewTask.Status.SUCCEEDED
                            || task.status() == ReviewTask.Status.FAILED
                            || task.status() == ReviewTask.Status.PARTIAL_SUCCEEDED;
                } catch (IOException | RuntimeException ex) {
                    cleanup();
                }
            }
        }

        private void sendErrorAndCleanup() {
            synchronized (sendLock) {
                if (cleaned.get()) return;
                try {
                    emitter.send(SseEmitter.event().name("stream-error")
                            .data(new StreamError("REVIEW_EVENT_STREAM_ERROR", "状态流暂时不可用"), MediaType.APPLICATION_JSON));
                } catch (IOException | RuntimeException ignored) {
                    // The client may already have disconnected.
                }
                cleanup();
            }
        }

        private void cleanup() {
            if (!cleaned.compareAndSet(false, true)) return;
            ScheduledFuture<?> scheduled = future;
            if (scheduled != null) scheduled.cancel(false);
            active.decrementAndGet();
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // Cleanup is idempotent even when the transport is already closed.
            }
        }
    }

    public record ReviewTaskStatusEvent(Long taskId, ReviewTask.Status status, long version,
                                        java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt,
                                        java.time.LocalDateTime startedAt, java.time.LocalDateTime finishedAt) {
        static ReviewTaskStatusEvent from(ReviewTask task) {
            return new ReviewTaskStatusEvent(task.id(), task.status(), task.version(), task.createdAt(),
                    task.updatedAt(), task.startedAt(), task.finishedAt());
        }
    }

    public record StreamError(String code, String message) {}
}
