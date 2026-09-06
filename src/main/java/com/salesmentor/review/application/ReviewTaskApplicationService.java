package com.salesmentor.review.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmentor.agent.application.SalesReviewAgent;
import com.salesmentor.agent.model.ReviewInput;
import com.salesmentor.agent.report.ReviewReport;
import com.salesmentor.review.domain.ReviewTask;
import com.salesmentor.review.domain.ReviewTaskRepository;
import com.salesmentor.review.domain.ReviewTaskInputConflictException;
import com.salesmentor.review.domain.ReviewTaskNotFoundException;
import com.salesmentor.trace.application.ReviewTaskTraceRecorder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ReviewTaskApplicationService {
    private static final String AGENT_FAILED = "AGENT_FAILED";
    private static final String REPORT_SERIALIZATION_FAILED = "REPORT_SERIALIZATION_FAILED";

    private final ReviewTaskRepository tasks;
    private final SalesReviewAgent agent;
    private final ObjectMapper objectMapper;
    private final ReviewTaskTraceRecorder traces;

    public ReviewTaskApplicationService(ReviewTaskRepository tasks, SalesReviewAgent agent,
                                        ObjectMapper objectMapper, ReviewTaskTraceRecorder traces) {
        if (tasks == null || agent == null || objectMapper == null || traces == null) {
            throw new IllegalArgumentException("review task service dependencies are required");
        }
        this.tasks = tasks;
        this.agent = agent;
        this.objectMapper = objectMapper;
        this.traces = traces;
    }

    public ReviewTask create(ReviewInput input) {
        if (input == null) throw new IllegalArgumentException("input is required");
        Optional<ReviewTask> existing = tasks.findByRequestId(input.requestId());
        if (existing.isPresent()) return sameInput(existing.get(), input);

        ReviewTask task = new ReviewTask(null, input.requestId(), null, null, input.industry(), input.salesStage(),
                input.customerRole(), input.conversationContent(), input.reviewGoal(), ReviewTask.Status.PENDING,
                null, null, null, 0, null, null, null, null, null, null);
        try {
            return tasks.save(task);
        } catch (DuplicateKeyException duplicate) {
            ReviewTask concurrent = tasks.findByRequestId(input.requestId())
                    .orElseThrow(() -> duplicate);
            return sameInput(concurrent, input);
        }
    }

    public ReviewTask find(Long taskId) {
        if (taskId == null || taskId <= 0) throw new IllegalArgumentException("taskId must be positive");
        return tasks.findById(taskId).orElseThrow(ReviewTaskNotFoundException::new);
    }

    public ReviewTask execute(Long taskId) {
        ReviewTask current = find(taskId);
        if (current.status() != ReviewTask.Status.PENDING) return current;
        long executionStarted = System.nanoTime();
        if (!tasks.start(current.id(), current.version())) return find(taskId);
        traces.claimed(current.id(), elapsedMillis(executionStarted));

        long claimedVersion = current.version() + 1;
        ReviewInput input = new ReviewInput(current.requestId(), current.industry(), current.salesStage(),
                current.customerRole(), current.conversationContent(), current.reviewGoal());
        ReviewReport report;
        long agentStarted = System.nanoTime();
        try {
            report = agent.review(input);
        } catch (RuntimeException ex) {
            return failAndRead(current.id(), claimedVersion, AGENT_FAILED, "review agent failed", executionStarted);
        }
        traces.agentCompleted(current.id(), elapsedMillis(agentStarted));

        String reportJson;
        try {
            reportJson = objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException | RuntimeException ex) {
            return failAndRead(current.id(), claimedVersion, REPORT_SERIALIZATION_FAILED,
                    "review report serialization failed", executionStarted);
        }
        if (tasks.succeed(current.id(), claimedVersion, reportJson)) {
            traces.succeeded(current.id(), elapsedMillis(executionStarted));
        }
        return find(taskId);
    }

    private ReviewTask failAndRead(Long id, long version, String code, String reason, long executionStarted) {
        if (tasks.fail(id, version, code, reason)) {
            traces.failed(id, elapsedMillis(executionStarted), code);
        }
        return find(id);
    }

    private long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }

    private ReviewTask sameInput(ReviewTask task, ReviewInput input) {
        if (!equal(task.industry(), input.industry())
                || task.salesStage() != input.salesStage()
                || !equal(task.customerRole(), input.customerRole())
                || !task.conversationContent().equals(input.conversationContent())
                || !task.reviewGoal().equals(input.reviewGoal())) {
            throw new ReviewTaskInputConflictException();
        }
        return task;
    }

    private boolean equal(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
