package com.salesmentor.port;

import com.salesmentor.domain.LongTermMemory;

import java.util.List;

public interface LongTermMemoryStore {
    void save(String userId, LongTermMemory memory);
    List<LongTermMemory> list(String userId);
}
