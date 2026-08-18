package io.github.portfolio.rag.adapter.local;

import io.github.portfolio.rag.config.RagProperties;
import io.github.portfolio.rag.domain.ChatMessage;
import io.github.portfolio.rag.domain.MemoryState;
import io.github.portfolio.rag.port.ConversationMemoryStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "app.rag", name = "mode", havingValue = "local", matchIfMissing = true)
public class InMemoryConversationMemoryStore implements ConversationMemoryStore {
    private final Map<String, MemoryState> sessions = new ConcurrentHashMap<>();
    private final int maxChars;

    public InMemoryConversationMemoryStore(RagProperties properties) {
        this.maxChars = properties.memoryMaxChars();
    }

    @Override
    public MemoryState get(String sessionId) {
        return sessions.getOrDefault(sessionId, MemoryState.empty());
    }

    @Override
    public synchronized MemoryState append(String sessionId, ChatMessage message) {
        MemoryState current = get(sessionId);
        List<ChatMessage> messages = new ArrayList<>(current.messages());
        messages.add(message);
        String summary = current.summary();
        int totalChars = summary.length() + messages.stream().mapToInt(value -> value.content().length()).sum();
        if (totalChars > maxChars && messages.size() > 4) {
            int compactCount = Math.max(2, messages.size() / 2);
            List<ChatMessage> compacted = new ArrayList<>(messages.subList(0, compactCount));
            summary = summarize(summary, compacted);
            messages = new ArrayList<>(messages.subList(compactCount, messages.size()));
        }
        MemoryState updated = new MemoryState(summary, messages);
        sessions.put(sessionId, updated);
        return updated;
    }

    @Override
    public void clear(String sessionId) {
        sessions.remove(sessionId);
    }

    static String summarize(String previous, List<ChatMessage> messages) {
        StringBuilder summary = new StringBuilder(previous);
        for (ChatMessage message : messages) {
            if (!summary.isEmpty()) summary.append("；");
            String content = message.content().replaceAll("\\s+", " ");
            summary.append(message.role()).append(':').append(content, 0, Math.min(content.length(), 100));
        }
        if (summary.length() > 1000) return summary.substring(summary.length() - 1000);
        return summary.toString();
    }
}
