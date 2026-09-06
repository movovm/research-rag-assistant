package com.salesmentor.agent.application;

import com.salesmentor.agent.evidence.CurrentConversationEvidenceExtractor;
import com.salesmentor.agent.model.ReviewInput;
import com.salesmentor.agent.model.ReviewPlan;
import com.salesmentor.agent.report.CurrentConversationEvidence;
import com.salesmentor.agent.report.EvidenceReference;
import com.salesmentor.agent.report.EvidenceSource;
import com.salesmentor.agent.report.ReportGroundingValidator;
import com.salesmentor.agent.report.ReviewReport;
import com.salesmentor.agent.runtime.BoundedAgentRuntime;
import com.salesmentor.agent.runtime.BoundedAgentRuntimeResult;
import com.salesmentor.experience.application.ExperienceSearchResult;
import com.salesmentor.knowledge.application.ProductKnowledgeSearchResult;

import java.util.ArrayList;
import java.util.List;

public final class SalesReviewAgent {
    private final com.salesmentor.agent.planner.ReviewPlanner planner;
    private final BoundedAgentRuntime runtime;
    private final CurrentConversationEvidenceExtractor evidenceExtractor;
    private final ReportGroundingValidator groundingValidator;

    public SalesReviewAgent(com.salesmentor.agent.planner.ReviewPlanner planner,
                            BoundedAgentRuntime runtime,
                            CurrentConversationEvidenceExtractor evidenceExtractor,
                            ReportGroundingValidator groundingValidator) {
        if (planner == null || runtime == null || evidenceExtractor == null || groundingValidator == null) {
            throw new IllegalArgumentException("agent dependencies are required");
        }
        this.planner = planner;
        this.runtime = runtime;
        this.evidenceExtractor = evidenceExtractor;
        this.groundingValidator = groundingValidator;
    }

    public ReviewReport review(ReviewInput input) {
        if (input == null) throw new IllegalArgumentException("input is required");
        ReviewPlan plan = planner.plan(input);
        BoundedAgentRuntimeResult runtimeResult = runtime.execute(plan);
        List<CurrentConversationEvidence> current = evidenceExtractor.extract(input.conversationContent());
        List<EvidenceReference> references = new ArrayList<>();
        List<ReviewReport.ReportItem> observations = new ArrayList<>();
        for (CurrentConversationEvidence evidence : current) {
            references.add(new EvidenceReference(evidence.referenceId(), EvidenceSource.CURRENT_CONVERSATION,
                    evidence.quote(), evidence.startOffset(), evidence.endOffset()));
            observations.add(new ReviewReport.ReportItem(evidence.quote(), List.of(evidence.referenceId())));
        }
        List<ReviewReport.ReportItem> historical = new ArrayList<>();
        for (ExperienceSearchResult result : runtimeResult.experienceResults()) {
            if (result.experienceId() == null || result.evidenceQuote() == null || result.evidenceQuote().isBlank()) continue;
            String id = "EXP-" + result.experienceId();
            references.add(new EvidenceReference(id, EvidenceSource.HISTORICAL_EXPERIENCE,
                    result.evidenceQuote(), -1, -1));
            historical.add(new ReviewReport.ReportItem(result.strategySummary() == null ? result.evidenceQuote() : result.strategySummary(), List.of(id)));
        }
        List<ReviewReport.ReportItem> products = new ArrayList<>();
        for (ProductKnowledgeSearchResult result : runtimeResult.productKnowledgeResults()) {
            if (result.referenceId() == null || result.excerpt() == null || result.excerpt().isBlank()) continue;
            references.add(new EvidenceReference(result.referenceId(), EvidenceSource.PRODUCT_KNOWLEDGE,
                    result.excerpt(), -1, -1));
            products.add(new ReviewReport.ReportItem(result.title() == null ? result.excerpt() : result.title(), List.of(result.referenceId())));
        }
        List<ReviewReport.ReportItem> recommendations = new ArrayList<>();
        if (!references.isEmpty()) {
            recommendations.add(new ReviewReport.ReportItem("基于当前已验证证据继续澄清客户需求", List.of(references.get(0).referenceId())));
        }
        ReviewReport report = new ReviewReport(observations, historical, products, recommendations,
                List.of("请确认下一步需要补充的事实"), references, runtimeResult.limitations());
        groundingValidator.validate(report, input.conversationContent(), runtimeResult);
        return report;
    }
}
