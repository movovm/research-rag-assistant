package com.salesmentor.adapter.cloud;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmentor.config.RagProperties;
import com.salesmentor.domain.ChatMessage;
import com.salesmentor.domain.MemoryState;
import com.salesmentor.port.ConversationMemoryStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.rag", name = "mode", havingValue = "cloud")
public class RedisConversationMemoryStore implements ConversationMemoryStore {
    private static final String PREFIX = "rag:conversation:";
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final int maxChars;

    public RedisConversationMemoryStore(StringRedisTemplate redis, ObjectMapper objectMapper, RagProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.maxChars = properties.memoryMaxChars();
    }

    @Override
    public MemoryState get(String sessionId) {
        String json = redis.opsForValue().get(PREFIX + sessionId);
        if (json == null) return MemoryState.empty();
        try {
            return objectMapper.readValue(json, MemoryState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot deserialize conversation memory", e);
        }
    }

    @Override
    public synchronized MemoryState append(String sessionId, ChatMessage message) {
        MemoryState current = get(sessionId);
        List<ChatMessage> messages = new ArrayList<>(current.messages());
        messages.add(message);
        String summary = current.summary();
        int size = summary.length() + messages.stream().mapToInt(item -> item.content().length()).sum();
        if (size > maxChars && messages.size() > 4) {
            int compactCount = Math.max(2, messages.size() / 2);
            summary = compact(summary, messages.subList(0, compactCount));
            messages = new ArrayList<>(messages.subList(compactCount, messages.size()));
        }
        MemoryState updated = new MemoryState(summary, messages);
        try {
            redis.opsForValue().set(PREFIX + sessionId, objectMapper.writeValueAsString(updated), Duration.ofDays(3));
            return updated;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize conversation memory", e);
        }
    }

    @Override
    public void clear(String sessionId) {
        redis.delete(PREFIX + sessionId);
    }

    private String compact(String previous, List<ChatMessage> messages) {
        String joined = messages.stream()
                .map(item -> item.role() + ":" + item.content().replaceAll("\\s+", " "))
                .reduce(previous, (left, right) -> left.isBlank() ? right : left + "；" + right);
        return joined.length() > 1000 ? joined.substring(joined.length() - 1000) : joined;
    }
}
