package com.salesmentor.agent.runtime;

import com.salesmentor.agent.model.ReviewToolName;

public record ToolExecutionOutcome(
        ReviewToolName toolName,
        ToolExecutionStatus status,
        int resultCount,
        String limitation
) {}
