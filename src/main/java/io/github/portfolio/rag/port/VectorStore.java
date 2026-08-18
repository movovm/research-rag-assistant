package io.github.portfolio.rag.port;

import io.github.portfolio.rag.domain.ChunkVector;
import io.github.portfolio.rag.domain.ScoredChunk;

import java.util.List;

public interface VectorStore {
    void upsert(List<ChunkVector> vectors);
    List<ScoredChunk> search(float[] queryVector, int topK);
    long count();
}
