package com.salesmentor.adapter.cloud;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmentor.domain.LongTermMemory;
import com.salesmentor.port.LongTermMemoryStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.rag", name = "mode", havingValue = "cloud")
public class RedisLongTermMemoryStore implements LongTermMemoryStore {
    private static final String PREFIX = "rag:profile-memory:";
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisLongTermMemoryStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void save(String userId, LongTermMemory memory) {
        List<LongTermMemory> values = new ArrayList<>(list(userId));
        values.add(memory);
        try {
            redis.opsForValue().set(PREFIX + userId, objectMapper.writeValueAsString(values));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize long-term memory", e);
        }
    }

    @Override
    public List<LongTermMemory> list(String userId) {
        String json = redis.opsForValue().get(PREFIX + userId);
        if (json == null) return List.of();
        try {
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, LongTermMemory.class);
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot deserialize long-term memory", e);
        }
    }
}
