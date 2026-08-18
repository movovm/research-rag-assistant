package io.github.portfolio.rag.core;

import io.github.portfolio.rag.adapter.local.InMemoryVectorStore;
import io.github.portfolio.rag.domain.LongTermMemory;
import io.github.portfolio.rag.port.EmbeddingProvider;
import io.github.portfolio.rag.port.LongTermMemoryStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class LongTermMemoryService {
    private final LongTermMemoryStore store;
    private final EmbeddingProvider embeddings;

    public LongTermMemoryService(LongTermMemoryStore store, EmbeddingProvider embeddings) {
        this.store = store;
        this.embeddings = embeddings;
    }

    public LongTermMemory add(String userId, String label, String content) {
        LongTermMemory memory = new LongTermMemory(UUID.randomUUID().toString(), label, content,
                embeddings.embed(label + " " + content, EmbeddingProvider.InputType.DOCUMENT), Instant.now());
        store.save(userId, memory);
        return memory;
    }

    public List<LongTermMemory> retrieve(String userId, String query, int topK) {
        float[] queryVector = embeddings.embed(query, EmbeddingProvider.InputType.QUERY);
        return store.list(userId).stream()
                .sorted(Comparator.comparingDouble((LongTermMemory value) ->
                        InMemoryVectorStore.cosine(queryVector, value.vector())).reversed())
                .limit(topK)
                .toList();
    }

    public List<LongTermMemory> list(String userId) {
        return store.list(userId);
    }
}
