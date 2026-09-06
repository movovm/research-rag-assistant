package com.salesmentor.agent.application;

import com.salesmentor.agent.evidence.CurrentConversationEvidenceExtractor;
import com.salesmentor.agent.planner.RuleBasedReviewPlanner;
import com.salesmentor.agent.report.ReportGroundingValidator;
import com.salesmentor.agent.report.ReviewReport;
import com.salesmentor.agent.runtime.BoundedAgentRuntime;
import com.salesmentor.agent.tool.ExperienceSearchTool;
import com.salesmentor.agent.tool.ProductKnowledgeTool;
import com.salesmentor.agent.tool.ToolRegistry;
import com.salesmentor.experience.application.ExperienceSearchApplicationService;
import com.salesmentor.experience.application.ExperienceSearchResult;
import com.salesmentor.knowledge.application.ProductKnowledgeSearchApplicationService;
import com.salesmentor.knowledge.application.ProductKnowledgeSearchResult;
import com.salesmentor.knowledge.domain.KnowledgeDocument;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SalesReviewAgentEndToEndTest {
    @Test
    void executesBothRealAdaptersInExperienceThenProductOrder() {
        ExperienceSearchApplicationService experienceService = mock(ExperienceSearchApplicationService.class);
        ProductKnowledgeSearchApplicationService productService = mock(ProductKnowledgeSearchApplicationService.class);
        when(experienceService.search(any())).thenReturn(List.of(experienceResult()));
        when(productService.search(any())).thenReturn(List.of(productResult()));

        SalesReviewAgent agent = agent(experienceService, productService);
        ReviewReport report = agent.review(input("Customer raises a price objection and asks about deployment specifications.",
                "Prepare an objection response with product facts."));

        InOrder order = inOrder(experienceService, productService);
        order.verify(experienceService).search(any());
        order.verify(productService).search(any());
        verifyNoMoreInteractions(experienceService, productService);
        assertThat(report.currentObservations()).extracting(ReviewReport.ReportItem::evidenceIds)
                .anyMatch(ids -> ids.stream().anyMatch(id -> id.startsWith("CUR-")));
        assertThat(report.historicalExperiences()).extracting(ReviewReport.ReportItem::evidenceIds)
                .allMatch(ids -> ids.stream().allMatch(id -> id.startsWith("EXP-")));
        assertThat(report.productFacts()).extracting(ReviewReport.ReportItem::evidenceIds)
                .allMatch(ids -> ids.stream().allMatch(id -> id.startsWith("DOC-")));
        assertThat(report.historicalExperiences()).isNotEmpty();
        assertThat(report.productFacts()).isNotEmpty();
    }

    @Test
    void noRetrievalSignalDoesNotCallEitherService() {
        ExperienceSearchApplicationService experienceService = mock(ExperienceSearchApplicationService.class);
        ProductKnowledgeSearchApplicationService productService = mock(ProductKnowledgeSearchApplicationService.class);

        ReviewReport report = agent(experienceService, productService)
                .review(input("The customer described the current meeting context.", "Summarize the conversation."));

        verifyNoInteractions(experienceService, productService);
        assertThat(report.currentObservations()).isNotEmpty();
        assertThat(report.limitations()).contains("no retrieval tool selected");
        assertThat(report.historicalExperiences()).isEmpty();
        assertThat(report.productFacts()).isEmpty();
        assertThat(report.references()).noneMatch(ref -> ref.referenceId().startsWith("EXP-")
                || ref.referenceId().startsWith("DOC-"));
    }

    @Test
    void experienceFailureStillRunsProductAndKeepsLimitations() {
        ExperienceSearchApplicationService experienceService = mock(ExperienceSearchApplicationService.class);
        ProductKnowledgeSearchApplicationService productService = mock(ProductKnowledgeSearchApplicationService.class);
        when(experienceService.search(any())).thenThrow(new IllegalStateException("controlled failure"));
        when(productService.search(any())).thenReturn(List.of(productResult()));

        ReviewReport report = agent(experienceService, productService)
                .review(input("Customer raises a price objection and asks about deployment.", "Find useful evidence."));

        InOrder order = inOrder(experienceService, productService);
        order.verify(experienceService).search(any());
        order.verify(productService).search(any());
        verifyNoMoreInteractions(experienceService, productService);
        assertThat(report.limitations()).contains("工具执行失败，无法取得当前证据");
        assertThat(report.historicalExperiences()).isEmpty();
        assertThat(report.productFacts()).hasSize(1);
    }

    @Test
    void productFailureDoesNotCreateProductFact() {
        ExperienceSearchApplicationService experienceService = mock(ExperienceSearchApplicationService.class);
        ProductKnowledgeSearchApplicationService productService = mock(ProductKnowledgeSearchApplicationService.class);
        when(experienceService.search(any())).thenReturn(List.of(experienceResult()));
        when(productService.search(any())).thenThrow(new IllegalStateException("controlled failure"));

        ReviewReport report = agent(experienceService, productService)
                .review(input("Customer raises a price objection and asks about deployment.", "Find useful evidence."));

        verify(experienceService).search(any());
        verify(productService).search(any());
        verifyNoMoreInteractions(experienceService, productService);
        assertThat(report.historicalExperiences()).hasSize(1);
        assertThat(report.productFacts()).isEmpty();
        assertThat(report.limitations()).contains("工具执行失败，无法取得当前证据");
    }

    @Test
    void malformedToolReferenceIsRejectedByRealGroundingValidator() {
        ExperienceSearchApplicationService experienceService = mock(ExperienceSearchApplicationService.class);
        ProductKnowledgeSearchApplicationService productService = mock(ProductKnowledgeSearchApplicationService.class);
        when(productService.search(any())).thenReturn(List.of(new ProductKnowledgeSearchResult(
                "BAD-1", 1L, "Deployment", KnowledgeDocument.DocumentType.DEPLOYMENT_GUIDE,
                "synthetic-deployment-guide.md", "Deployment details", 1)));

        assertThatThrownBy(() -> agent(experienceService, productService)
                .review(input("The customer asks about deployment specifications.", "Provide product facts.")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(productService).search(any());
        verifyNoInteractions(experienceService);
    }

    private SalesReviewAgent agent(ExperienceSearchApplicationService experienceService,
                                   ProductKnowledgeSearchApplicationService productService) {
        ToolRegistry registry = new ToolRegistry(List.of(
                new ExperienceSearchTool(experienceService), new ProductKnowledgeTool(productService)));
        return new SalesReviewAgent(new RuleBasedReviewPlanner(), new BoundedAgentRuntime(registry),
                new CurrentConversationEvidenceExtractor(), new ReportGroundingValidator());
    }

    private com.salesmentor.agent.model.ReviewInput input(String conversation, String goal) {
        return new com.salesmentor.agent.model.ReviewInput("e2e-request", null, null, null, conversation, goal);
    }

    private ExperienceSearchResult experienceResult() {
        return new ExperienceSearchResult(7L, 70L, null, null, null, "buyer", "Customer objected to price",
                "Clarify total cost and value", "What outcome matters most?", "Customer raises a price objection.",
                0, 38, 0.8, 1);
    }

    private ProductKnowledgeSearchResult productResult() {
        return new ProductKnowledgeSearchResult("DOC-11", 11L, "Deployment guide",
                KnowledgeDocument.DocumentType.DEPLOYMENT_GUIDE, "synthetic-deployment-guide.md",
                "Deployment specifications are documented here.", 2);
    }
}
