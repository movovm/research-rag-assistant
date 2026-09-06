package com.salesmentor.review;

import com.salesmentor.agent.application.SalesReviewAgent;
import com.salesmentor.agent.runtime.BoundedAgentRuntime;
import com.salesmentor.agent.tool.ToolRegistry;
import com.salesmentor.experience.application.ExperienceSearchApplicationService;
import com.salesmentor.knowledge.application.ProductKnowledgeSearchApplicationService;
import com.salesmentor.review.config.ReviewAgentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReviewAgentConfigurationTest {
    @Test
    void createsRealBoundedAgentWithExactlyTwoRegisteredTools() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ExperienceSearchApplicationService.class,
                    () -> mock(ExperienceSearchApplicationService.class));
            context.registerBean(ProductKnowledgeSearchApplicationService.class,
                    () -> mock(ProductKnowledgeSearchApplicationService.class));
            context.register(ReviewAgentConfiguration.class);
            context.refresh();

            ToolRegistry registry = context.getBean(ToolRegistry.class);
            assertThat(registry.registeredToolNames()).containsExactlyInAnyOrder(
                    com.salesmentor.agent.model.ReviewToolName.EXPERIENCE_SEARCH,
                    com.salesmentor.agent.model.ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH);
            assertThat(context.getBean(BoundedAgentRuntime.class)).isNotNull();
            assertThat(context.getBean(SalesReviewAgent.class)).isNotNull();
        }
    }
}
