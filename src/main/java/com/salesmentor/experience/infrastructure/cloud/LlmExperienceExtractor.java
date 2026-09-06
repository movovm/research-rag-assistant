package com.salesmentor.experience.infrastructure.cloud;
import com.salesmentor.experience.application.ExperienceExtractor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.List;
@Component @ConditionalOnProperty(prefix="app.rag",name="mode",havingValue="cloud")
public class LlmExperienceExtractor implements ExperienceExtractor { public ExtractionBatch extract(ExtractionCommand c){throw new IllegalStateException("cloud Experience extractor requires a configured structured LLM adapter");} }
