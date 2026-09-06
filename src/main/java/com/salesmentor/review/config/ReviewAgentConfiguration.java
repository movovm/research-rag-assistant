package com.salesmentor.review.config;

import com.salesmentor.agent.application.SalesReviewAgent;
import com.salesmentor.agent.evidence.CurrentConversationEvidenceExtractor;
import com.salesmentor.agent.planner.RuleBasedReviewPlanner;
import com.salesmentor.agent.report.ReportGroundingValidator;
import com.salesmentor.agent.runtime.BoundedAgentRuntime;
import com.salesmentor.agent.tool.ExperienceSearchTool;
import com.salesmentor.agent.tool.ProductKnowledgeTool;
import com.salesmentor.agent.tool.ToolRegistry;
import com.salesmentor.experience.application.ExperienceSearchApplicationService;
import com.salesmentor.knowledge.application.ProductKnowledgeSearchApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ReviewAgentConfiguration {
    @Bean
    public RuleBasedReviewPlanner reviewPlanner() {
        return new RuleBasedReviewPlanner();
    }

    @Bean
    public CurrentConversationEvidenceExtractor currentConversationEvidenceExtractor() {
        return new CurrentConversationEvidenceExtractor();
    }

    @Bean
    public ReportGroundingValidator reportGroundingValidator() {
        return new ReportGroundingValidator();
    }

    @Bean
    public ExperienceSearchTool experienceSearchTool(ExperienceSearchApplicationService service) {
        return new ExperienceSearchTool(service);
    }

    @Bean
    public ProductKnowledgeTool productKnowledgeTool(ProductKnowledgeSearchApplicationService service) {
        return new ProductKnowledgeTool(service);
    }

    @Bean
    public ToolRegistry reviewToolRegistry(ExperienceSearchTool experienceTool,
                                           ProductKnowledgeTool productTool) {
        return new ToolRegistry(List.of(experienceTool, productTool));
    }

    @Bean
    public BoundedAgentRuntime boundedAgentRuntime(ToolRegistry registry) {
        return new BoundedAgentRuntime(registry);
    }

    @Bean
    public SalesReviewAgent salesReviewAgent(RuleBasedReviewPlanner planner,
                                             BoundedAgentRuntime runtime,
                                             CurrentConversationEvidenceExtractor extractor,
                                             ReportGroundingValidator validator) {
        return new SalesReviewAgent(planner, runtime, extractor, validator);
    }
}
