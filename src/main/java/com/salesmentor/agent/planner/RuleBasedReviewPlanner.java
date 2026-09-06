package com.salesmentor.agent.planner;

import com.salesmentor.agent.model.ReviewInput;
import com.salesmentor.agent.model.ReviewPlan;
import com.salesmentor.agent.model.ReviewToolName;
import com.salesmentor.experience.application.ExperienceQuery;
import com.salesmentor.knowledge.application.ProductKnowledgeQuery;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;

public final class RuleBasedReviewPlanner implements ReviewPlanner {
    @Override
    public ReviewPlan plan(ReviewInput input) {
        if (input == null) throw new IllegalArgumentException("input is required");
        String signals = (input.conversationContent() + " " + input.reviewGoal()).toLowerCase(Locale.ROOT);
        String experienceSignals = signals.replace("price explanation", "")
                .replace("pricing explanation", "")
                .replace("价格说明", "");
        boolean experience = containsAny(experienceSignals, "price", "pricing", "objection", "negotiate", "negotiation", "异议", "价格", "谈判");
        boolean product = containsAny(signals, "product", "capability", "deployment", "specification", "spec", "feature", "price explanation", "pricing explanation", "产品", "能力", "部署", "规格", "价格说明");
        EnumSet<ReviewToolName> tools = EnumSet.noneOf(ReviewToolName.class);
        Map<ReviewToolName, String> reasons = new EnumMap<>(ReviewToolName.class);
        ExperienceQuery experienceQuery = null;
        ProductKnowledgeQuery productQuery = null;
        String queryText = input.conversationContent() + " " + input.reviewGoal();
        if (experience) {
            tools.add(ReviewToolName.EXPERIENCE_SEARCH);
            reasons.put(ReviewToolName.EXPERIENCE_SEARCH, "sales objection or negotiation signal");
            experienceQuery = new ExperienceQuery(queryText, null, null, input.salesStage(), input.customerRole(), 5);
        }
        if (product) {
            tools.add(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH);
            reasons.put(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH, "product fact signal");
            productQuery = new ProductKnowledgeQuery(queryText, input.industry(),
                    input.salesStage() == null ? null : input.salesStage().name(), input.customerRole(), 5);
        }
        return new ReviewPlan(tools, experienceQuery, productQuery, reasons);
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }
}
