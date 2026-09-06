package com.salesmentor.agent.runtime;

import com.salesmentor.agent.model.ReviewPlan;
import com.salesmentor.agent.model.ReviewToolName;
import com.salesmentor.agent.tool.ExperienceSearchTool;
import com.salesmentor.agent.tool.ProductKnowledgeTool;
import com.salesmentor.agent.tool.ReviewReadOnlyTool;
import com.salesmentor.agent.tool.ToolRegistry;
import com.salesmentor.experience.application.ExperienceQuery;
import com.salesmentor.experience.application.ExperienceSearchApplicationService;
import com.salesmentor.knowledge.application.ProductKnowledgeQuery;
import com.salesmentor.knowledge.application.ProductKnowledgeSearchApplicationService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BoundedAgentRuntimeTest {
    @Test void emptyPlanCallsNoTools() {
        BoundedAgentRuntime runtime = new BoundedAgentRuntime(registry());
        BoundedAgentRuntimeResult result = runtime.execute(new ReviewPlan(Set.of(), null, null, Map.of()));
        assertThat(result.experienceResults()).isEmpty();
        assertThat(result.productKnowledgeResults()).isEmpty();
        assertThat(result.outcomes()).isEmpty();
        assertThat(result.limitations()).contains("no retrieval tool selected");
    }

    @Test void executesOnlySelectedToolsInExperienceThenProductOrder() {
        ExperienceSearchApplicationService experiences = mock(ExperienceSearchApplicationService.class);
        ProductKnowledgeSearchApplicationService products = mock(ProductKnowledgeSearchApplicationService.class);
        ExperienceSearchTool experienceTool = new ExperienceSearchTool(experiences);
        ProductKnowledgeTool productTool = new ProductKnowledgeTool(products);
        when(experiences.search(any())).thenReturn(List.of());
        when(products.search(any())).thenReturn(List.of());
        BoundedAgentRuntime runtime = new BoundedAgentRuntime(new ToolRegistry(List.of(experienceTool, productTool)));
        runtime.execute(plan(true, true));
        InOrder order = inOrder(experiences, products);
        order.verify(experiences).search(any());
        order.verify(products).search(any());
        verifyNoMoreInteractions(experiences, products);
    }

    @Test void executesOnlyExperienceTool() {
        ExperienceSearchApplicationService experiences = mock(ExperienceSearchApplicationService.class);
        ProductKnowledgeSearchApplicationService products = mock(ProductKnowledgeSearchApplicationService.class);
        when(experiences.search(any())).thenReturn(List.of());
        BoundedAgentRuntimeResult result = new BoundedAgentRuntime(new ToolRegistry(List.of(
                new ExperienceSearchTool(experiences), new ProductKnowledgeTool(products)))).execute(plan(true, false));
        assertThat(result.outcomes()).extracting(ToolExecutionOutcome::toolName)
                .containsExactly(ReviewToolName.EXPERIENCE_SEARCH);
        assertThat(result.limitations()).contains("evidence insufficient");
        verify(experiences).search(any());
        verifyNoInteractions(products);
    }

    @Test void executesOnlyProductTool() {
        ExperienceSearchApplicationService experiences = mock(ExperienceSearchApplicationService.class);
        ProductKnowledgeSearchApplicationService products = mock(ProductKnowledgeSearchApplicationService.class);
        when(products.search(any())).thenReturn(List.of());
        BoundedAgentRuntimeResult result = new BoundedAgentRuntime(new ToolRegistry(List.of(
                new ExperienceSearchTool(experiences), new ProductKnowledgeTool(products)))).execute(plan(false, true));
        assertThat(result.outcomes()).extracting(ToolExecutionOutcome::toolName)
                .containsExactly(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH);
        assertThat(result.limitations()).contains("evidence insufficient");
        verify(products).search(any());
        verifyNoInteractions(experiences);
    }

    @Test void marksSingleToolFailureAsEvidenceInsufficient() {
        ExperienceSearchApplicationService experiences = mock(ExperienceSearchApplicationService.class);
        ProductKnowledgeSearchApplicationService products = mock(ProductKnowledgeSearchApplicationService.class);
        when(experiences.search(any())).thenThrow(new IllegalStateException("hidden"));
        BoundedAgentRuntimeResult result = new BoundedAgentRuntime(new ToolRegistry(List.of(
                new ExperienceSearchTool(experiences), new ProductKnowledgeTool(products)))).execute(plan(true, false));
        assertThat(result.outcomes()).extracting(ToolExecutionOutcome::status)
                .containsExactly(ToolExecutionStatus.FAILED);
        assertThat(result.limitations()).contains("evidence insufficient");
        verify(experiences).search(any());
        verifyNoInteractions(products);
    }

    @Test void continuesWithProductAfterExperienceFailure() {
        ExperienceSearchApplicationService experiences = mock(ExperienceSearchApplicationService.class);
        ProductKnowledgeSearchApplicationService products = mock(ProductKnowledgeSearchApplicationService.class);
        when(experiences.search(any())).thenThrow(new IllegalStateException("hidden"));
        when(products.search(any())).thenReturn(List.of());
        BoundedAgentRuntime runtime = new BoundedAgentRuntime(new ToolRegistry(List.of(
                new ExperienceSearchTool(experiences), new ProductKnowledgeTool(products))));
        BoundedAgentRuntimeResult result = runtime.execute(plan(true, true));
        assertThat(result.outcomes()).extracting(ToolExecutionOutcome::status)
                .containsExactly(ToolExecutionStatus.FAILED, ToolExecutionStatus.EMPTY);
        assertThat(result.limitations()).contains("evidence insufficient");
        verify(experiences).search(any());
        verify(products).search(any());
    }

    @Test void marksEmptyAndBothFailuresAsEvidenceInsufficient() {
        ExperienceSearchApplicationService experiences = mock(ExperienceSearchApplicationService.class);
        ProductKnowledgeSearchApplicationService products = mock(ProductKnowledgeSearchApplicationService.class);
        when(experiences.search(any())).thenReturn(List.of());
        when(products.search(any())).thenReturn(List.of());
        BoundedAgentRuntimeResult result = new BoundedAgentRuntime(new ToolRegistry(List.of(
                new ExperienceSearchTool(experiences), new ProductKnowledgeTool(products)))).execute(plan(true, true));
        assertThat(result.outcomes()).extracting(ToolExecutionOutcome::status)
                .containsExactly(ToolExecutionStatus.EMPTY, ToolExecutionStatus.EMPTY);
        assertThat(result.limitations()).contains("evidence insufficient");
    }

    @Test void rejectsNullPlanAndKeepsResultsImmutable() {
        assertThatThrownBy(() -> new BoundedAgentRuntime(registry()).execute(null))
                .isInstanceOf(IllegalArgumentException.class);
        BoundedAgentRuntimeResult result = new BoundedAgentRuntime(registry()).execute(new ReviewPlan(Set.of(), null, null, Map.of()));
        assertThatThrownBy(() -> result.experienceResults().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.productKnowledgeResults().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.limitations().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.outcomes().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void treatsWrongRegisteredToolTypeAsConfigurationFailure() {
        ReviewReadOnlyTool wrong = () -> ReviewToolName.EXPERIENCE_SEARCH;
        ProductKnowledgeSearchApplicationService products = mock(ProductKnowledgeSearchApplicationService.class);
        when(products.search(any())).thenReturn(List.of());
        BoundedAgentRuntimeResult result = new BoundedAgentRuntime(new ToolRegistry(List.of(
                wrong, new ProductKnowledgeTool(products)))).execute(plan(true, true));
        assertThat(result.outcomes().get(0).status()).isEqualTo(ToolExecutionStatus.FAILED);
        assertThat(result.outcomes().get(1).status()).isEqualTo(ToolExecutionStatus.EMPTY);
        verify(products).search(any());
    }

    private ToolRegistry registry() {
        return new ToolRegistry(List.of(
                new ExperienceSearchTool(mock(ExperienceSearchApplicationService.class)),
                new ProductKnowledgeTool(mock(ProductKnowledgeSearchApplicationService.class))));
    }

    private ReviewPlan plan(boolean experience, boolean product) {
        return new ReviewPlan(
                experience && product ? Set.of(ReviewToolName.EXPERIENCE_SEARCH, ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH)
                        : experience ? Set.of(ReviewToolName.EXPERIENCE_SEARCH) : Set.of(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH),
                experience ? new ExperienceQuery("price", null, null, null, null, 1) : null,
                product ? new ProductKnowledgeQuery("product", null, null, null, 1) : null,
                experience && product ? Map.of(ReviewToolName.EXPERIENCE_SEARCH, "reason", ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH, "reason")
                        : experience ? Map.of(ReviewToolName.EXPERIENCE_SEARCH, "reason") : Map.of(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH, "reason"));
    }
}
