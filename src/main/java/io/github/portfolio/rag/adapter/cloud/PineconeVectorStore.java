package io.github.portfolio.rag.adapter.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.portfolio.rag.config.RagProperties;
import io.github.portfolio.rag.domain.ChunkVector;
import io.github.portfolio.rag.domain.DocumentChunk;
import io.github.portfolio.rag.domain.ScoredChunk;
import io.github.portfolio.rag.port.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(prefix = "app.rag", name = "mode", havingValue = "cloud")
public class PineconeVectorStore implements VectorStore {
    private final RagProperties.Cloud config;
    private final RestClient client;
    private final AtomicLong count = new AtomicLong();

    public PineconeVectorStore(RagProperties properties, RestClient.Builder builder) {
        this.config = properties.cloud();
        require(config.pineconeApiKey(), "PINECONE_API_KEY");
        require(config.pineconeHost(), "PINECONE_HOST");
        this.client = builder.baseUrl(config.pineconeHost()).defaultHeader("Api-Key", config.pineconeApiKey()).build();
    }

    @Override
    public void upsert(List<ChunkVector> vectors) {
        List<Map<String, Object>> payload = vectors.stream().map(item -> {
            Map<String, Object> metadata = new HashMap<>(item.chunk().metadata());
            metadata.put("documentId", item.chunk().documentId());
            metadata.put("content", item.chunk().content());
            Map<String, Object> vector = new HashMap<>();
            vector.put("id", item.chunk().id());
            vector.put("values", item.vector());
            vector.put("metadata", metadata);
            return vector;
        }).toList();
        client.post().uri("/vectors/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("vectors", payload, "namespace", config.pineconeNamespace()))
                .retrieve().toBodilessEntity();
        count.addAndGet(vectors.size());
    }

    @Override
    public List<ScoredChunk> search(float[] queryVector, int topK) {
        JsonNode response = client.post().uri("/query")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("vector", queryVector, "topK", topK, "includeMetadata", true,
                        "namespace", config.pineconeNamespace()))
                .retrieve().body(JsonNode.class);
        List<ScoredChunk> results = new ArrayList<>();
        int rank = 1;
        for (JsonNode match : response.path("matches")) {
            JsonNode metadata = match.path("metadata");
            String id = match.path("id").asText();
            DocumentChunk chunk = new DocumentChunk(id, metadata.path("documentId").asText(),
                    metadata.path("source").asText(), metadata.path("documentType").asText(),
                    metadata.path("project").asText(), metadata.path("content").asText(), Map.of());
            results.add(new ScoredChunk(chunk, 0, match.path("score").asDouble(), 0, rank++));
        }
        return results;
    }

    @Override
    public long count() {
        return count.get();
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required in cloud mode");
    }
}
