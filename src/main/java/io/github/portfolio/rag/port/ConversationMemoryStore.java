package io.github.portfolio.rag.port;

import io.github.portfolio.rag.domain.ChatMessage;
import io.github.portfolio.rag.domain.MemoryState;

public interface ConversationMemoryStore {
    MemoryState get(String sessionId);
    MemoryState append(String sessionId, ChatMessage message);
    void clear(String sessionId);
}
