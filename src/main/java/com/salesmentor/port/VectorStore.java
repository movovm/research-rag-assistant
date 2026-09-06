package com.salesmentor.port;

import com.salesmentor.domain.ChunkVector;
import com.salesmentor.domain.ScoredChunk;

import java.util.List;

public interface VectorStore {
    void upsert(List<ChunkVector> vectors);
    List<ScoredChunk> search(float[] queryVector, int topK);
    long count();
}
