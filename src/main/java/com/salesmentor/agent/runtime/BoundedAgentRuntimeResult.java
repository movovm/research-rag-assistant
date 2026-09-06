package com.salesmentor.agent.runtime;

import com.salesmentor.experience.application.ExperienceSearchResult;
import com.salesmentor.knowledge.application.ProductKnowledgeSearchResult;

import java.util.List;

public record BoundedAgentRuntimeResult(
        List<ExperienceSearchResult> experienceResults,
        List<ProductKnowledgeSearchResult> productKnowledgeResults,
        List<ToolExecutionOutcome> outcomes,
        List<String> limitations
) {
    public BoundedAgentRuntimeResult {
        experienceResults = experienceResults == null ? List.of() : List.copyOf(experienceResults);
        productKnowledgeResults = productKnowledgeResults == null ? List.of() : List.copyOf(productKnowledgeResults);
        outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }
}
