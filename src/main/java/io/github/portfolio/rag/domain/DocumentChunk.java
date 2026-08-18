package io.github.portfolio.rag.domain;

import java.util.Map;

public record DocumentChunk(
        String id,
        String documentId,
        String source,
        String documentType,
        String project,
        String content,
        Map<String, String> metadata
) {}
