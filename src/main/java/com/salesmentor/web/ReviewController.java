package com.salesmentor.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmentor.agent.model.ReviewInput;
import com.salesmentor.agent.report.ReviewReport;
import com.salesmentor.review.application.ReviewTaskApplicationService;
import com.salesmentor.review.application.ReviewTaskSubmissionService;
import com.salesmentor.review.application.ReviewTaskEventSubscriptionService;
import com.salesmentor.review.application.ReviewSubscriptionCapacityException;
import com.salesmentor.review.domain.ReviewTask;
import com.salesmentor.review.domain.ReviewTaskInputConflictException;
import com.salesmentor.review.domain.ReviewTaskNotFoundException;
import com.salesmentor.trace.domain.AgentTrace;
import com.salesmentor.trace.domain.AgentTraceRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Comparator;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewTaskApplicationService tasks;
    private final ReviewTaskSubmissionService submissions;
    private final AgentTraceRepository traces;
    private final ObjectMapper objectMapper;
    private final ReviewTaskEventSubscriptionService eventSubscriptions;

    public ReviewController(ReviewTaskApplicationService tasks, ReviewTaskSubmissionService submissions,
                            AgentTraceRepository traces, ObjectMapper objectMapper,
                            ReviewTaskEventSubscriptionService eventSubscriptions) {
        this.tasks = tasks;
        this.submissions = submissions;
        this.traces = traces;
        this.objectMapper = objectMapper;
        this.eventSubscriptions = eventSubscriptions;
    }

    @PostMapping
    public ResponseEntity<SubmissionResponse> create(@Valid @RequestBody CreateReviewRequest request) {
        ReviewInput input = request.toInput();
        ReviewTask task = tasks.create(input);
        ReviewTaskSubmissionService.ReviewTaskSubmission submission = submissions.submit(task.id());
        HttpStatus status = switch (submission.status()) {
            case ACCEPTED -> HttpStatus.ACCEPTED;
            case REJECTED -> HttpStatus.SERVICE_UNAVAILABLE;
            case NOT_PENDING -> HttpStatus.OK;
        };
        String errorCode = submission.status() == ReviewTaskSubmissionService.ReviewTaskSubmission.Status.REJECTED
                ? "REVIEW_QUEUE_FULL" : null;
        return ResponseEntity.status(status).body(new SubmissionResponse(task.id(), submission.status(),
                submission.taskStatus(), statusUrl(task.id()), errorCode));
    }

    @GetMapping("/{taskId}")
    public ReviewTaskResponse get(@PathVariable Long taskId) {
        ReviewTask task = tasks.find(taskId);
        ReviewReport report = null;
        if (task.status() == ReviewTask.Status.SUCCEEDED) {
            if (task.reportJson() == null || task.reportJson().isBlank()) {
                throw new IllegalStateException("review report snapshot is unavailable");
            }
            try {
                report = objectMapper.readValue(task.reportJson(), ReviewReport.class);
            } catch (JsonProcessingException | RuntimeException ex) {
                throw new IllegalStateException("review report snapshot is invalid");
            }
        }
        return new ReviewTaskResponse(task.id(), task.status(), task.createdAt(), task.updatedAt(),
                task.startedAt(), task.finishedAt(), report, task.failureCode());
    }

    @GetMapping("/{taskId}/traces")
    public List<TraceResponse> traces(@PathVariable Long taskId) {
        tasks.find(taskId);
        return this.traces.findByTaskId(taskId).stream()
                .filter(ReviewController::isSafeTaskTrace)
                .sorted(Comparator.comparingInt(AgentTrace::stepNo))
                .map(TraceResponse::from)
                .toList();
    }

    @GetMapping(value = "/{taskId}/events", produces = "text/event-stream")
    public SseEmitter events(@PathVariable Long taskId) {
        return eventSubscriptions.subscribe(taskId);
    }

    private String statusUrl(Long taskId) {
        return "/api/reviews/" + taskId;
    }

    private static boolean isSafeTaskTrace(AgentTrace trace) {
        return trace != null && trace.stepType() == AgentTrace.StepType.TASK
                && ((trace.stepNo() == 1 && trace.status() == AgentTrace.Status.STARTED
                && "review task claimed".equals(trace.outputSummary()))
                || (trace.stepNo() == 2 && trace.status() == AgentTrace.Status.SUCCEEDED
                && "review agent completed".equals(trace.outputSummary()))
                || (trace.stepNo() == 3 && trace.status() == AgentTrace.Status.SUCCEEDED
                && "review task succeeded".equals(trace.outputSummary()))
                || (trace.stepNo() == 3 && trace.status() == AgentTrace.Status.FAILED
                && "review task failed".equals(trace.outputSummary())));
    }

    @ExceptionHandler(ReviewTaskInputConflictException.class)
    public ResponseEntity<ApiError> conflict(ReviewTaskInputConflictException ignored) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("REVIEW_INPUT_CONFLICT", "requestId conflicts with existing input"));
    }

    @ExceptionHandler(ReviewTaskNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ReviewTaskNotFoundException ignored) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("REVIEW_TASK_NOT_FOUND", "review task not found"));
    }

    @ExceptionHandler(ReviewSubscriptionCapacityException.class)
    public ResponseEntity<ApiError> subscriptionCapacity(ReviewSubscriptionCapacityException ignored) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("REVIEW_EVENT_CAPACITY_REACHED", "状态订阅容量已满"));
    }

    @ExceptionHandler(RejectedExecutionException.class)
    public ResponseEntity<ApiError> schedulerRejected(RejectedExecutionException ignored) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("REVIEW_EVENT_CAPACITY_REACHED", "状态订阅容量已满"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> invalidSnapshot(IllegalStateException ignored) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "服务暂时不可用，请查看服务日志"));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> malformedRequest(Exception ignored) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "请求参数不合法"));
    }

    public record CreateReviewRequest(
            @NotBlank @Size(max = 64) String requestId,
            @Size(max = 64) String industry,
            com.salesmentor.salescase.domain.SalesCase.SalesStage salesStage,
            @Size(max = 64) String customerRole,
            @NotBlank @Size(max = 20_000) String conversationContent,
            @NotBlank @Size(max = 2_000) String reviewGoal) {
        ReviewInput toInput() {
            return new ReviewInput(requestId, industry, salesStage, customerRole, conversationContent, reviewGoal);
        }
    }

    public record SubmissionResponse(Long taskId,
                                     ReviewTaskSubmissionService.ReviewTaskSubmission.Status submissionStatus,
                                     ReviewTask.Status taskStatus, String statusUrl, String errorCode) {}

    public record ReviewTaskResponse(Long taskId, ReviewTask.Status status, LocalDateTime createdAt,
                                     LocalDateTime updatedAt, LocalDateTime startedAt, LocalDateTime finishedAt,
                                     ReviewReport report, String failureCode) {}

    public record TraceResponse(int stepNo, AgentTrace.StepType stepType, AgentTrace.Status status,
                                long durationMs, String errorCode, String outputSummary, LocalDateTime createdAt) {
        static TraceResponse from(AgentTrace trace) {
            return new TraceResponse(trace.stepNo(), trace.stepType(), trace.status(), Math.max(0, trace.durationMs()),
                    safeErrorCode(trace.errorCode()), trace.outputSummary(), trace.createdAt());
        }

        private static String safeErrorCode(String value) {
            return "AGENT_FAILED".equals(value) || "REPORT_SERIALIZATION_FAILED".equals(value) ? value : null;
        }
    }

    public record ApiError(String code, String message) {}
}
