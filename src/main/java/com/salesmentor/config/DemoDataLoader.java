package com.salesmentor.config;

import com.salesmentor.core.DocumentIngestionService;
import com.salesmentor.core.LongTermMemoryService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class DemoDataLoader {
    private final RagProperties properties;
    private final DocumentIngestionService ingestion;
    private final LongTermMemoryService memories;

    public DemoDataLoader(RagProperties properties, DocumentIngestionService ingestion, LongTermMemoryService memories) {
        this.properties = properties;
        this.ingestion = ingestion;
        this.memories = memories;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void load() throws IOException {
        if (!properties.seedDemoData()) return;
        seed("demo-documents/redis-guide.md", "Redis 开发规范.md", "研发规范", "基础设施");
        seed("demo-documents/rag-design.md", "RAG 系统设计.md", "技术方案", "科研知识库");
        seed("demo-documents/onboarding.md", "新人开发手册.md", "新人培训", "团队协作");
        if (memories.list("demo-user").isEmpty()) {
            memories.add("demo-user", "回答偏好", "先给结论，再给可执行步骤；技术术语需要附简短解释。 ");
        }
    }

    private void seed(String path, String source, String type, String project) throws IOException {
        String content = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        ingestion.ingestText(source, type, project, content);
    }
}
