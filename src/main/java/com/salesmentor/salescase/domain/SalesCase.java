package com.salesmentor.salescase.domain;

import java.time.LocalDateTime;

public record SalesCase(
        Long id,
        String externalKey,
        String title,
        SourceType sourceType,
        String sourceUri,
        String industry,
        SalesStage salesStage,
        String customerRole,
        String content,
        Status status,
        String extractError,
        int version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public enum SourceType { PUBLIC, SYNTHETIC, USER_PROVIDED }

    public enum SalesStage { PROSPECTING, DISCOVERY, PROPOSAL, NEGOTIATION, CLOSING, AFTER_SALES }

    public enum Status { IMPORTED, EXTRACTING, EXTRACTED, EXTRACT_FAILED }
}
