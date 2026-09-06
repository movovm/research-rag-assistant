package com.salesmentor.review.application;

import com.salesmentor.review.domain.ReviewTask;
import com.salesmentor.review.domain.ReviewTaskRepository;
import com.salesmentor.trace.application.ReviewTaskTraceRecorder;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class ReviewTaskTimeoutService {
    static final long DEFAULT_TIMEOUT_SECONDS = 120;
    static final long DEFAULT_SCAN_SECONDS = 5;
    static final int DEFAULT_BATCH_SIZE = 100;
    private final ReviewTaskRepository tasks;
    private final ReviewTaskTraceRecorder traces;
    private final ScheduledThreadPoolExecutor scheduler;
    private final Clock clock;
    private final long timeoutSeconds;
    private final long scanSeconds;
    private final int batchSize;

    @Autowired
    public ReviewTaskTimeoutService(ReviewTaskRepository tasks, ReviewTaskTraceRecorder traces,
                                    @Qualifier("reviewTaskTimeoutScheduler") ScheduledThreadPoolExecutor scheduler,
                                    @Value("${salesmentor.review.timeout-seconds:120}") long timeoutSeconds,
                                    @Value("${salesmentor.review.timeout-scan-seconds:5}") long scanSeconds,
                                    @Value("${salesmentor.review.timeout-batch-size:100}") int batchSize) {
        this(tasks, traces, scheduler, Clock.systemDefaultZone(), timeoutSeconds, scanSeconds, batchSize);
    }

    public ReviewTaskTimeoutService(ReviewTaskRepository tasks, ReviewTaskTraceRecorder traces,
                             ScheduledThreadPoolExecutor scheduler, Clock clock,
                             long timeoutSeconds, long scanSeconds, int batchSize) {
        if (tasks == null || traces == null || scheduler == null || clock == null) {
            throw new IllegalArgumentException("timeout service dependencies are required");
        }
        if (timeoutSeconds <= 0 || scanSeconds <= 0 || batchSize <= 0 || batchSize > 1000) {
            throw new IllegalArgumentException("timeout configuration is invalid");
        }
        this.tasks = tasks;
        this.traces = traces;
        this.scheduler = scheduler;
        this.clock = clock;
        this.timeoutSeconds = timeoutSeconds;
        this.scanSeconds = scanSeconds;
        this.batchSize = batchSize;
    }

    @PostConstruct
    void schedule() {
        scheduler.scheduleWithFixedDelay(this::scanSafely, scanSeconds, scanSeconds, TimeUnit.SECONDS);
    }

    public int scanOnce(LocalDateTime now) {
        if (now == null) throw new IllegalArgumentException("time is required");
        LocalDateTime deadline = now.minusSeconds(timeoutSeconds);
        List<ReviewTask> candidates = tasks.findExpiredRunning(deadline, batchSize);
        int closed = 0;
        for (ReviewTask candidate : candidates) {
            if (tasks.timeout(candidate.id(), candidate.version(), deadline)) {
                long duration = Math.max(0, Duration.between(candidate.startedAt(), now).toMillis());
                traces.failed(candidate.id(), duration, "REVIEW_EXECUTION_TIMEOUT");
                closed++;
            }
        }
        return closed;
    }

    void scanSafely() {
        try {
            scanOnce(LocalDateTime.now(clock));
        } catch (RuntimeException ignored) {
            // Infrastructure failures leave the task as-is; the next scan retries.
        }
    }
}
