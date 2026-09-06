package com.salesmentor.domain;

public record ScoredChunk(
        DocumentChunk chunk,
        double bm25Score,
        double denseScore,
        double combinedScore,
        int rank
) {}
