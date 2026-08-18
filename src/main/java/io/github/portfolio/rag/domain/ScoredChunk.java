package io.github.portfolio.rag.domain;

public record ScoredChunk(
        DocumentChunk chunk,
        double bm25Score,
        double denseScore,
        double combinedScore,
        int rank
) {}
