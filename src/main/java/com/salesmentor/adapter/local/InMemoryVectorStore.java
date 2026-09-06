package com.salesmentor.adapter.local;

import com.salesmentor.domain.ChunkVector;
import com.salesmentor.domain.ScoredChunk;
import com.salesmentor.port.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "app.rag", name = "mode", havingValue = "local", matchIfMissing = true)
public class InMemoryVectorStore implements VectorStore {
    private final Map<String, ChunkVector> vectors = new ConcurrentHashMap<>();

    @Override
    public void upsert(List<ChunkVector> items) {
        items.forEach(item -> vectors.put(item.chunk().id(), item));
    }

    @Override
    public List<ScoredChunk> search(float[] queryVector, int topK) {
        return vectors.values().stream()
                .map(item -> new ScoredChunk(item.chunk(), 0, cosine(queryVector, item.vector()), 0, 0))
                .sorted(Comparator.comparingDouble(ScoredChunk::denseScore).reversed())
                .limit(topK)
                .toList();
    }

    @Override
    public long count() {
        return vectors.size();
    }

    public static double cosine(float[] left, float[] right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        int length = Math.min(left.length, right.length);
        for (int i = 0; i < length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) return 0;
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
