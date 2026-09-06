package com.salesmentor.agent.planner;

import com.salesmentor.agent.model.ReviewPlan;
import com.salesmentor.agent.model.ReviewToolName;

public final class ReviewPlanValidator {
    private ReviewPlanValidator() {}

    public static void validate(ReviewPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan is required");
        }
        validateComponents(plan.selectedTools(), plan.experienceQuery(), plan.productKnowledgeQuery(), plan.reasons());
    }

    public static void validateComponents(java.util.Set<ReviewToolName> selectedTools,
                                          com.salesmentor.experience.application.ExperienceQuery experienceQuery,
                                          com.salesmentor.knowledge.application.ProductKnowledgeQuery productKnowledgeQuery,
                                          java.util.Map<ReviewToolName, String> reasons) {
        if (selectedTools == null || selectedTools.size() > 2) {
            throw new IllegalArgumentException("at most two tools are allowed");
        }
        if (reasons == null) {
            throw new IllegalArgumentException("reasons are required");
        }
        for (ReviewToolName tool : selectedTools) {
            if (tool == null) throw new IllegalArgumentException("tool is required");
            String reason = reasons.get(tool);
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("selected tool requires a reason");
            }
        }
        if (selectedTools.contains(ReviewToolName.EXPERIENCE_SEARCH) != (experienceQuery != null)) {
            throw new IllegalArgumentException("experience tool and query must match");
        }
        if (selectedTools.contains(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH) != (productKnowledgeQuery != null)) {
            throw new IllegalArgumentException("product tool and query must match");
        }
        for (ReviewToolName tool : reasons.keySet()) {
            if (!selectedTools.contains(tool)) {
                throw new IllegalArgumentException("unselected tool cannot have a reason");
            }
        }
    }
}
