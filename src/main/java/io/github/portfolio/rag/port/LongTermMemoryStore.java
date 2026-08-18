package io.github.portfolio.rag.port;

import io.github.portfolio.rag.domain.LongTermMemory;

import java.util.List;

public interface LongTermMemoryStore {
    void save(String userId, LongTermMemory memory);
    List<LongTermMemory> list(String userId);
}
