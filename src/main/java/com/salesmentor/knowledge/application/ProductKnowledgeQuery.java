package com.salesmentor.knowledge.application;

public record ProductKnowledgeQuery(
        String queryText,
        String industry,
        String salesStage,
        String customerRole,
        int topK
) {
    public ProductKnowledgeQuery {
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("queryText is required");
        }
        if (topK < 1 || topK > 10) {
            throw new IllegalArgumentException("topK must be between 1 and 10");
        }
    }
}
