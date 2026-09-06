package com.salesmentor.agent.report;

public record CurrentConversationEvidence(
        String referenceId,
        String quote,
        int startOffset,
        int endOffset
) {}
