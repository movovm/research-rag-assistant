package io.github.portfolio.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        String mode,
        int topK,
        double denseWeight,
        int chunkMinChars,
        int chunkMaxChars,
        double semanticThreshold,
        int memoryMaxChars,
        boolean seedDemoData,
        Cloud cloud
) {
    public record Cloud(
            String dashscopeApiKey,
            String embeddingModel,
            String chatModel,
            int embeddingDimension,
            String pineconeApiKey,
            String pineconeHost,
            String pineconeNamespace
    ) {}
}
