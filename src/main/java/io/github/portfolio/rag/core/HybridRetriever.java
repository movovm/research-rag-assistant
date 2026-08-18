package io.github.portfolio.rag.core;

import io.github.portfolio.rag.config.RagProperties;
import io.github.portfolio.rag.domain.ScoredChunk;
import io.github.portfolio.rag.port.EmbeddingProvider;
import io.github.portfolio.rag.port.VectorStore;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HybridRetriever {
    private final LexicalIndex lexicalIndex;
    private final VectorStore vectorStore;
    private final EmbeddingProvider embeddings;
    private final RagProperties properties;

    public HybridRetriever(LexicalIndex lexicalIndex, VectorStore vectorStore, EmbeddingProvider embeddings, RagProperties properties) {
        this.lexicalIndex = lexicalIndex;
        this.vectorStore = vectorStore;
        this.embeddings = embeddings;
        this.properties = properties;
    }

    public List<ScoredChunk> retrieve(String query) {
        int candidateCount = Math.max(properties.topK() * 4, 20);
        List<ScoredChunk> lexical = lexicalIndex.search(query, candidateCount);
        List<ScoredChunk> semantic = vectorStore.search(embeddings.embed(query, EmbeddingProvider.InputType.QUERY), candidateCount);
        double maxBm25 = lexical.stream().mapToDouble(ScoredChunk::bm25Score).max().orElse(1);
        double maxDense = semantic.stream().mapToDouble(ScoredChunk::denseScore).max().orElse(1);

        Map<String, MutableScore> merged = new LinkedHashMap<>();
        lexical.forEach(item -> merged.computeIfAbsent(item.chunk().id(), ignored -> new MutableScore(item)).bm25 = item.bm25Score());
        semantic.forEach(item -> merged.computeIfAbsent(item.chunk().id(), ignored -> new MutableScore(item)).dense = item.denseScore());

        double alpha = properties.denseWeight();
        List<ScoredChunk> sorted = new ArrayList<>(merged.values().stream().map(value -> {
                    double bm25Normalized = maxBm25 == 0 ? 0 : value.bm25 / maxBm25;
                    double denseNormalized = maxDense == 0 ? 0 : Math.max(0, value.dense / maxDense);
                    double combined = alpha * denseNormalized + (1 - alpha) * bm25Normalized;
                    return new ScoredChunk(value.base.chunk(), value.bm25, value.dense, combined, 0);
                })
                .sorted(Comparator.comparingDouble(ScoredChunk::combinedScore).reversed())
                .limit(properties.topK())
                .toList());

        for (int i = 0; i < sorted.size(); i++) {
            ScoredChunk value = sorted.get(i);
            sorted.set(i, new ScoredChunk(value.chunk(), value.bm25Score(), value.denseScore(), value.combinedScore(), i + 1));
        }
        return sorted;
    }

    private static class MutableScore {
        private final ScoredChunk base;
        private double bm25;
        private double dense;

        private MutableScore(ScoredChunk base) {
            this.base = base;
        }
    }
}
