package io.github.portfolio.rag.domain;

import java.time.Instant;

public record LongTermMemory(
        String id,
        String label,
        String content,
        float[] vector,
        Instant createdAt
) {}
