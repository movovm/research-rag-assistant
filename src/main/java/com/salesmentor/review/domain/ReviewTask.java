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
        long version,
        String failureCode,
        String failureReason,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public ReviewTask(Long id, String requestId, Long userId, String sessionId, String industry,
                      SalesStage salesStage, String customerRole, String conversationContent,
                      String reviewGoal, Status status, String planJson, String reportJson,
                      String partialReason, LocalDateTime startedAt, LocalDateTime finishedAt,
                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, requestId, userId, sessionId, industry, salesStage, customerRole, conversationContent,
                reviewGoal, status, planJson, reportJson, partialReason, 0L, null, null,
                startedAt, finishedAt, createdAt, updatedAt);
    }

    public ReviewTask {
        requireNonBlank(requestId, "requestId");
        requireNonBlank(conversationContent, "conversationContent");
        if (status == null) throw new IllegalArgumentException("status is required");
        if (version < 0) throw new IllegalArgumentException("version must be non-negative");
        if (status != Status.PARTIAL_SUCCEEDED) requireNonBlank(reviewGoal, "reviewGoal");
        if (failureCode != null && (failureCode.isBlank() || failureCode.length() > 64)) {
            throw new IllegalArgumentException("failureCode is invalid");
        }
        if (failureReason != null && (failureReason.isBlank() || failureReason.length() > 500)) {
            throw new IllegalArgumentException("failureReason is invalid");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    public enum Status { PENDING, RUNNING, SUCCEEDED, PARTIAL_SUCCEEDED, FAILED }
}
