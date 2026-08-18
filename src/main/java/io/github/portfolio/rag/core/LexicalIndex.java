package io.github.portfolio.rag.core;

import io.github.portfolio.rag.domain.DocumentChunk;
import io.github.portfolio.rag.domain.ScoredChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LexicalIndex {
    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final TextTokenizer tokenizer;
    private final Map<String, DocumentChunk> chunks = new ConcurrentHashMap<>();
    private final Map<String, List<String>> chunkTokens = new ConcurrentHashMap<>();

    public LexicalIndex(TextTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    public void upsert(List<DocumentChunk> documents) {
        documents.forEach(chunk -> {
            chunks.put(chunk.id(), chunk);
            chunkTokens.put(chunk.id(), tokenizer.tokenize(chunk.content()));
        });
    }

    public List<ScoredChunk> search(String query, int topK) {
        List<String> queryTerms = tokenizer.tokenize(query);
        if (queryTerms.isEmpty() || chunks.isEmpty()) return List.of();

        double averageLength = chunkTokens.values().stream().mapToInt(List::size).average().orElse(1);
        Map<String, Integer> documentFrequency = documentFrequency(new HashSet<>(queryTerms));
        List<ScoredChunk> results = new ArrayList<>();

        chunkTokens.forEach((chunkId, terms) -> {
            Map<String, Integer> frequencies = frequencies(terms);
            double score = 0;
            for (String term : queryTerms) {
                int frequency = frequencies.getOrDefault(term, 0);
                if (frequency == 0) continue;
                int df = documentFrequency.getOrDefault(term, 0);
                double idf = Math.log(1 + (chunks.size() - df + 0.5) / (df + 0.5));
                double numerator = frequency * (K1 + 1);
                double denominator = frequency + K1 * (1 - B + B * terms.size() / averageLength);
                score += idf * numerator / denominator;
            }
            if (score > 0) {
                results.add(new ScoredChunk(chunks.get(chunkId), score, 0, 0, 0));
            }
        });

        return results.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::bm25Score).reversed())
                .limit(topK)
                .toList();
    }

    public List<DocumentChunk> allChunks() {
        return List.copyOf(chunks.values());
    }

    private Map<String, Integer> documentFrequency(Set<String> terms) {
        Map<String, Integer> result = new HashMap<>();
        for (List<String> tokens : chunkTokens.values()) {
            Set<String> unique = new HashSet<>(tokens);
            for (String term : terms) {
                if (unique.contains(term)) result.merge(term, 1, Integer::sum);
            }
        }
        return result;
    }

    private Map<String, Integer> frequencies(List<String> terms) {
        Map<String, Integer> result = new HashMap<>();
        terms.forEach(term -> result.merge(term, 1, Integer::sum));
        return result;
    }
}
