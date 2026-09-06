package com.salesmentor.agent.model;

import com.salesmentor.agent.planner.ReviewPlanValidator;
import com.salesmentor.experience.application.ExperienceQuery;
import com.salesmentor.knowledge.application.ProductKnowledgeQuery;

import java.util.Map;
import java.util.Set;

public record ReviewPlan(
        Set<ReviewToolName> selectedTools,
        ExperienceQuery experienceQuery,
        ProductKnowledgeQuery productKnowledgeQuery,
        Map<ReviewToolName, String> reasons
) {
    public ReviewPlan {
        selectedTools = selectedTools == null ? Set.of() : Set.copyOf(selectedTools);
        reasons = reasons == null ? Map.of() : Map.copyOf(reasons);
        ReviewPlanValidator.validateComponents(selectedTools, experienceQuery, productKnowledgeQuery, reasons);
    }
}
