package com.salesmentor.knowledge.domain;

import java.time.LocalDateTime;

public record KnowledgeDocument(
        Long id,
        String title,
        DocumentType documentType,
        String sourceName,
        String content,
        String contentHash,
        Status status,
        IndexStatus indexStatus,
        String vectorNamespace,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public enum DocumentType { PRODUCT_OVERVIEW, FAQ, PRICING_NOTE, COMPETITOR_NOTE, DEPLOYMENT_GUIDE }

    public enum Status { DRAFT, PUBLISHED }

    public enum IndexStatus { NOT_INDEXED, INDEXING, INDEXED, FAILED }
}
