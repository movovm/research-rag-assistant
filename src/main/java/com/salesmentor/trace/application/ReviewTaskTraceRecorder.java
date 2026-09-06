package com.salesmentor.trace.application;

import com.salesmentor.trace.domain.AgentTrace;
import com.salesmentor.trace.domain.AgentTraceRepository;
import org.springframework.stereotype.Component;

/** Records fixed, non-sensitive task lifecycle events without affecting task state. */
@Component
public class ReviewTaskTraceRecorder {
    private final AgentTraceRepository traces;

    public ReviewTaskTraceRecorder(AgentTraceRepository traces) {
        if (traces == null) throw new IllegalArgumentException("trace repository is required");
        this.traces = traces;
    }

    public void claimed(Long taskId, long durationMs) {
        append(taskId, 1, "review task claimed", AgentTrace.Status.STARTED, durationMs, null);
    }

    public void agentCompleted(Long taskId, long durationMs) {
        append(taskId, 2, "review agent completed", AgentTrace.Status.SUCCEEDED, durationMs, null);
    }

    public void succeeded(Long taskId, long durationMs) {
        append(taskId, 3, "review task succeeded", AgentTrace.Status.SUCCEEDED, durationMs, null);
    }

    public void failed(Long taskId, long durationMs, String failureCode) {
        if (!"AGENT_FAILED".equals(failureCode) && !"REPORT_SERIALIZATION_FAILED".equals(failureCode)
                && !"REVIEW_EXECUTION_TIMEOUT".equals(failureCode)) {
            throw new IllegalArgumentException("unsupported trace failure code");
        }
        append(taskId, 3, "review task failed", AgentTrace.Status.FAILED, durationMs, failureCode);
    }

    private void append(Long taskId, int stepNo, String summary, AgentTrace.Status status, long durationMs,
                        String errorCode) {
        if (taskId == null || taskId <= 0) throw new IllegalArgumentException("taskId must be positive");
        try {
            traces.append(new AgentTrace(null, taskId, stepNo, AgentTrace.StepType.TASK, null, null, summary,
                    null, Math.max(0, durationMs), status, errorCode, null));
        } catch (RuntimeException ignored) {
            // Trace observability must never alter the ReviewTask fact source.
        }
    }
}
