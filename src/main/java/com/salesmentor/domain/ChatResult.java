package com.salesmentor.domain;

import java.util.List;

public record ChatResult(
        String answer,
        String originalQuestion,
        String rewrittenQuery,
        String memorySummary,
        List<LongTermMemory> longTermMemories,
        List<ScoredChunk> evidence,
        List<String> stages
) {}
