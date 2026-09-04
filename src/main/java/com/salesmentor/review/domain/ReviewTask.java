package com.salesmentor.review.domain;

import com.salesmentor.salescase.domain.SalesCase.SalesStage;

import java.time.LocalDateTime;

public record ReviewTask(
        Long id,
        String requestId,
        Long userId,
        String sessionId,
        String industry,
        SalesStage salesStage,
        String customerRole,
        String conversationContent,
        String reviewGoal,
        Status status,
        String planJson,
        String reportJson,
        String partialReason,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public enum Status { PENDING, RUNNING, SUCCEEDED, PARTIAL_SUCCEEDED, FAILED }
}
