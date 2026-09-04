package com.salesmentor.experience.domain;

import com.salesmentor.salescase.domain.SalesCase.SalesStage;

import java.time.LocalDateTime;

public record ExperienceUnit(
        Long id,
        Long caseId,
        ScenarioType scenarioType,
        ObjectionType objectionType,
        SalesStage salesStage,
        String customerRole,
        String triggerText,
        String strategySummary,
        String recommendedQuestion,
        String evidenceQuote,
        int evidenceStart,
        int evidenceEnd,
        String applicability,
        String contentHash,
        ReviewStatus reviewStatus,
        IndexStatus indexStatus,
        String vectorRef,
        String extractionModel,
        String promptVersion,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        int version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public enum ScenarioType { DISCOVERY, NEED_CONFIRMATION, OBJECTION_HANDLING, VALUE_COMMUNICATION, NEGOTIATION, FOLLOW_UP }

    public enum ObjectionType { PRICE, COMPETITOR, AUTHORITY, NEED, TIMING, RISK, IMPLEMENTATION, OTHER }

    public enum ReviewStatus { GENERATED, VERIFIED, PUBLISHED, REJECTED }

    public enum IndexStatus { NOT_INDEXED, INDEXING, INDEXED, FAILED }
}
