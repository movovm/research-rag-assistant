package com.salesmentor.domain;

import java.time.Instant;

public record LongTermMemory(
        String id,
        String label,
        String content,
        float[] vector,
        Instant createdAt
) {}
