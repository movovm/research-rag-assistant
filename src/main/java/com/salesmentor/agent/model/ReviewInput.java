package com.salesmentor.agent.model;

import com.salesmentor.salescase.domain.SalesCase;

public record ReviewInput(
        String requestId,
        String industry,
        SalesCase.SalesStage salesStage,
        String customerRole,
        String conversationContent,
        String reviewGoal
) {
    public ReviewInput {
        requireNonBlank(requestId, "requestId");
        requireLength(conversationContent, "conversationContent", 20_000);
        requireLength(reviewGoal, "reviewGoal", 2_000);
    }

    private static void requireLength(String value, String name, int max) {
        requireNonBlank(value, name);
        if (value.length() > max) {
            throw new IllegalArgumentException(name + " exceeds maximum length");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
