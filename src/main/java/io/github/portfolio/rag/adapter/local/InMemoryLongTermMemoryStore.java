package io.github.portfolio.rag.adapter.local;

import io.github.portfolio.rag.domain.LongTermMemory;
import io.github.portfolio.rag.port.LongTermMemoryStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@ConditionalOnProperty(prefix = "app.rag", name = "mode", havingValue = "local", matchIfMissing = true)
public class InMemoryLongTermMemoryStore implements LongTermMemoryStore {
    private final Map<String, List<LongTermMemory>> memories = new ConcurrentHashMap<>();

    @Override
    public void save(String userId, LongTermMemory memory) {
        memories.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(memory);
    }

    @Override
    public List<LongTermMemory> list(String userId) {
        return List.copyOf(memories.getOrDefault(userId, List.of()));
    }
}
