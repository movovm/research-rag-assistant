package com.salesmentor.agent.runtime;

import com.salesmentor.agent.model.ReviewPlan;
import com.salesmentor.agent.model.ReviewToolName;
import com.salesmentor.agent.planner.ReviewPlanValidator;
import com.salesmentor.agent.tool.ExperienceSearchTool;
import com.salesmentor.agent.tool.ProductKnowledgeTool;
import com.salesmentor.agent.tool.ReviewReadOnlyTool;
import com.salesmentor.agent.tool.ToolRegistry;
import com.salesmentor.experience.application.ExperienceSearchResult;
import com.salesmentor.knowledge.application.ProductKnowledgeSearchResult;

import java.util.ArrayList;
import java.util.List;

public final class BoundedAgentRuntime {
    private static final String EMPTY_LIMITATION = "未找到已发布且已索引的匹配证据";
    private static final String FAILED_LIMITATION = "工具执行失败，无法取得当前证据";
    private static final String INSUFFICIENT_LIMITATION = "evidence insufficient";

    private final ToolRegistry registry;

    public BoundedAgentRuntime(ToolRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("registry is required");
        this.registry = registry;
    }

    public BoundedAgentRuntimeResult execute(ReviewPlan plan) {
        if (plan == null) throw new IllegalArgumentException("plan is required");
        ReviewPlanValidator.validate(plan);
        List<ExperienceSearchResult> experiences = new ArrayList<>();
        List<ProductKnowledgeSearchResult> products = new ArrayList<>();
        List<ToolExecutionOutcome> outcomes = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        if (plan.selectedTools().isEmpty()) {
            limitations.add("no retrieval tool selected");
            return new BoundedAgentRuntimeResult(experiences, products, outcomes, limitations);
        }

        if (plan.selectedTools().contains(ReviewToolName.EXPERIENCE_SEARCH)) {
            executeExperience(plan, experiences, outcomes, limitations);
        }
        if (plan.selectedTools().contains(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH)) {
            executeProduct(plan, products, outcomes, limitations);
        }
        if (!outcomes.isEmpty() && outcomes.stream().allMatch(value -> value.status() != ToolExecutionStatus.SUCCESS)) {
            limitations.add(INSUFFICIENT_LIMITATION);
        }
        return new BoundedAgentRuntimeResult(experiences, products, outcomes, limitations);
    }

    private void executeExperience(ReviewPlan plan, List<ExperienceSearchResult> results,
                                   List<ToolExecutionOutcome> outcomes, List<String> limitations) {
        try {
            ReviewReadOnlyTool registered = registry.require(ReviewToolName.EXPERIENCE_SEARCH);
            if (!(registered instanceof ExperienceSearchTool tool) || tool.name() != ReviewToolName.EXPERIENCE_SEARCH) {
                throw new IllegalStateException("experience tool configuration is invalid");
            }
            List<ExperienceSearchResult> found = tool.search(plan.experienceQuery());
            if (found == null) throw new IllegalStateException("experience tool returned null");
            results.addAll(found);
            recordOutcome(ReviewToolName.EXPERIENCE_SEARCH, found.size(), outcomes, limitations);
        } catch (RuntimeException ignored) {
            outcomes.add(new ToolExecutionOutcome(ReviewToolName.EXPERIENCE_SEARCH, ToolExecutionStatus.FAILED, 0, FAILED_LIMITATION));
            limitations.add(FAILED_LIMITATION);
        }
    }

    private void executeProduct(ReviewPlan plan, List<ProductKnowledgeSearchResult> results,
                                List<ToolExecutionOutcome> outcomes, List<String> limitations) {
        try {
            ReviewReadOnlyTool registered = registry.require(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH);
            if (!(registered instanceof ProductKnowledgeTool tool) || tool.name() != ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH) {
                throw new IllegalStateException("product tool configuration is invalid");
            }
            List<ProductKnowledgeSearchResult> found = tool.search(plan.productKnowledgeQuery());
            if (found == null) throw new IllegalStateException("product tool returned null");
            results.addAll(found);
            recordOutcome(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH, found.size(), outcomes, limitations);
        } catch (RuntimeException ignored) {
            outcomes.add(new ToolExecutionOutcome(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH, ToolExecutionStatus.FAILED, 0, FAILED_LIMITATION));
            limitations.add(FAILED_LIMITATION);
        }
    }

    private void recordOutcome(ReviewToolName name, int count, List<ToolExecutionOutcome> outcomes,
                               List<String> limitations) {
        if (count == 0) {
            outcomes.add(new ToolExecutionOutcome(name, ToolExecutionStatus.EMPTY, 0, EMPTY_LIMITATION));
            limitations.add(EMPTY_LIMITATION);
        } else {
            outcomes.add(new ToolExecutionOutcome(name, ToolExecutionStatus.SUCCESS, count, null));
        }
    }
}
