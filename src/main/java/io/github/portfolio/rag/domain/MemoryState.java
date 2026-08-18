package io.github.portfolio.rag.domain;

import java.util.ArrayList;
import java.util.List;

public record MemoryState(String summary, List<ChatMessage> messages) {
    public MemoryState {
        summary = summary == null ? "" : summary;
        messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }

    public static MemoryState empty() {
        return new MemoryState("", List.of());
    }
}
