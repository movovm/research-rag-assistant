package com.salesmentor.trace.domain;

import java.time.LocalDateTime;

public record AgentTrace(
        Long id,
        Long taskId,
        int stepNo,
        StepType stepType,
        String toolName,
        String inputJson,
        String outputSummary,
        String evidenceIds,
        long durationMs,
        Status status,
        String errorCode,
        LocalDateTime createdAt
) {
    public enum StepType { TASK, PLAN, TOOL, GENERATE, VALIDATE, FALLBACK }

    public enum Status { STARTED, SUCCEEDED, FAILED, SKIPPED }
}
