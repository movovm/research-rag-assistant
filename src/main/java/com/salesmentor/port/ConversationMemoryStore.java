package com.salesmentor.port;

import com.salesmentor.domain.ChatMessage;
import com.salesmentor.domain.MemoryState;

public interface ConversationMemoryStore {
    MemoryState get(String sessionId);
    MemoryState append(String sessionId, ChatMessage message);
    void clear(String sessionId);
}
