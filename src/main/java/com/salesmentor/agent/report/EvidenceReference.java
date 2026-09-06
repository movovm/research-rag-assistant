package com.salesmentor.agent.report;

public record EvidenceReference(
        String referenceId,
        EvidenceSource source,
        String quote,
        int startOffset,
        int endOffset
) {}
