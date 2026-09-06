package com.salesmentor.knowledge.application;

import com.salesmentor.knowledge.domain.KnowledgeDocument;

public record ProductKnowledgeSearchResult(
        String referenceId,
        Long documentId,
        String title,
        KnowledgeDocument.DocumentType documentType,
        String sourceName,
        String excerpt,
        int score
) {}
