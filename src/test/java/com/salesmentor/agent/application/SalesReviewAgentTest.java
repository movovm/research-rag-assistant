package com.salesmentor.agent.application;

import com.salesmentor.agent.evidence.CurrentConversationEvidenceExtractor;
import com.salesmentor.agent.model.ReviewInput;
import com.salesmentor.agent.model.ReviewPlan;
import com.salesmentor.agent.model.ReviewToolName;
import com.salesmentor.agent.report.ReportGroundingValidator;
import com.salesmentor.agent.report.ReviewReport;
import com.salesmentor.agent.runtime.BoundedAgentRuntime;
import com.salesmentor.agent.runtime.BoundedAgentRuntimeResult;
import com.salesmentor.agent.runtime.ToolExecutionOutcome;
import com.salesmentor.agent.runtime.ToolExecutionStatus;
import com.salesmentor.agent.planner.ReviewPlanner;
import com.salesmentor.experience.application.ExperienceSearchResult;
import com.salesmentor.knowledge.application.ProductKnowledgeSearchResult;
import com.salesmentor.knowledge.domain.KnowledgeDocument;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SalesReviewAgentTest {
    private final ReviewInput input = new ReviewInput("req", null, null, null,
            "Customer asks about price.\nSales explains total cost.", "plan response");

    @Test void emptyPlanStillReturnsCurrentEvidenceAndLimitation() {
        ReviewPlanner planner = mock(ReviewPlanner.class);
        BoundedAgentRuntime runtime = mock(BoundedAgentRuntime.class);
        when(planner.plan(input)).thenReturn(new ReviewPlan(Set.of(), null, null, Map.of()));
        when(runtime.execute(any())).thenReturn(result(List.of(), List.of(), "no retrieval tool selected"));
        ReviewReport report = agent(planner, runtime).review(input);
        assertThat(report.currentObservations()).hasSize(2);
        assertThat(report.limitations()).contains("no retrieval tool selected");
        verify(runtime).execute(any());
    }

    @Test void includesOnlyExperienceEvidenceForExperiencePlan() {
        ReviewPlanner planner = mock(ReviewPlanner.class);
        BoundedAgentRuntime runtime = mock(BoundedAgentRuntime.class);
        ReviewPlan plan = new ReviewPlan(Set.of(ReviewToolName.EXPERIENCE_SEARCH),
                new com.salesmentor.experience.application.ExperienceQuery("price", null, null, null, null, 1), null,
                Map.of(ReviewToolName.EXPERIENCE_SEARCH, "reason"));
        when(planner.plan(input)).thenReturn(plan);
        ExperienceSearchResult experience = new ExperienceSearchResult(7L, 70L, null, null, null, null,
                "trigger", "Use total cost", "question", "Sales explains total cost.", 0, 1, 0.1, 1);
        when(runtime.execute(plan)).thenReturn(result(List.of(experience), List.of(), null));
        ReviewReport report = agent(planner, runtime).review(input);
        assertThat(report.historicalExperiences()).hasSize(1);
        assertThat(report.productFacts()).isEmpty();
    }

    @Test void includesOnlyProductEvidenceForProductPlan() {
        ReviewPlanner planner = mock(ReviewPlanner.class);
        BoundedAgentRuntime runtime = mock(BoundedAgentRuntime.class);
        ReviewPlan plan = new ReviewPlan(Set.of(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH), null,
                new com.salesmentor.knowledge.application.ProductKnowledgeQuery("price", null, null, null, 1),
                Map.of(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH, "reason"));
        when(planner.plan(input)).thenReturn(plan);
        ProductKnowledgeSearchResult product = new ProductKnowledgeSearchResult("DOC-3", 3L, "Pricing",
                KnowledgeDocument.DocumentType.PRICING_NOTE, "synthetic-pricing-note.md", "Pricing details", 1);
        when(runtime.execute(plan)).thenReturn(result(List.of(), List.of(product), null));
        ReviewReport report = agent(planner, runtime).review(input);
        assertThat(report.productFacts()).hasSize(1);
        assertThat(report.historicalExperiences()).isEmpty();
    }

    @Test void preservesRuntimeFailureLimitationAndValidatesRealGrounding() {
        ReviewPlanner planner = mock(ReviewPlanner.class);
        BoundedAgentRuntime runtime = mock(BoundedAgentRuntime.class);
        when(planner.plan(input)).thenReturn(new ReviewPlan(Set.of(), null, null, Map.of()));
        when(runtime.execute(any())).thenReturn(result(List.of(), List.of(), "evidence insufficient"));
        ReviewReport report = agent(planner, runtime).review(input);
        assertThat(report.limitations()).containsExactly("evidence insufficient");
    }

    @Test void rejectsNullInputAndKeepsReportCollectionsImmutable() {
        assertThatThrownBy(() -> agent(mock(ReviewPlanner.class), mock(BoundedAgentRuntime.class)).review(null))
                .isInstanceOf(IllegalArgumentException.class);
        ReviewPlanner planner = mock(ReviewPlanner.class);
        BoundedAgentRuntime runtime = mock(BoundedAgentRuntime.class);
        when(planner.plan(input)).thenReturn(new ReviewPlan(Set.of(), null, null, Map.of()));
        when(runtime.execute(any())).thenReturn(result(List.of(), List.of(), "no retrieval tool selected"));
        ReviewReport report = agent(planner, runtime).review(input);
        assertThatThrownBy(() -> report.currentObservations().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> report.references().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> report.limitations().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void hasOnlyTheFourAllowedDependencies() {
        Field[] fields = SalesReviewAgent.class.getDeclaredFields();
        assertThat(fields).extracting(Field::getType).containsExactlyInAnyOrder(
                ReviewPlanner.class, BoundedAgentRuntime.class, CurrentConversationEvidenceExtractor.class, ReportGroundingValidator.class);
    }

    private SalesReviewAgent agent(ReviewPlanner planner, BoundedAgentRuntime runtime) {
        return new SalesReviewAgent(planner, runtime, new CurrentConversationEvidenceExtractor(), new ReportGroundingValidator());
    }

    private BoundedAgentRuntimeResult result(List<ExperienceSearchResult> experiences,
                                             List<ProductKnowledgeSearchResult> products, String limitation) {
        return new BoundedAgentRuntimeResult(experiences, products, List.of(), limitation == null ? List.of() : List.of(limitation));
    }
}
