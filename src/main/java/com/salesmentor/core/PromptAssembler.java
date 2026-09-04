package com.salesmentor.core;

import com.salesmentor.domain.LongTermMemory;
import com.salesmentor.domain.MemoryState;
import com.salesmentor.domain.ScoredChunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PromptAssembler {
    public String assemble(String question, String rewritten, MemoryState shortMemory,
                           List<LongTermMemory> longMemories, List<ScoredChunk> evidence) {
        String profile = longMemories.stream().map(value -> value.label() + ": " + value.content()).collect(Collectors.joining("\n"));
        String conversation = shortMemory.messages().stream().map(value -> value.role() + ": " + value.content()).collect(Collectors.joining("\n"));
        String sources = evidence.stream().map(value -> "[" + value.rank() + "] " + value.chunk().source() + "\n" + value.chunk().content())
                .collect(Collectors.joining("\n\n"));
        return """
                [长期偏好]
                %s

                [短期会话摘要]
                %s

                [最近对话]
                %s

                [RAG 检索证据]
                %s

                [原始问题]
                %s

                [独立查询]
                %s

                请仅基于检索证据回答，并列出来源。证据不足时不要猜测。
                """.formatted(profile, shortMemory.summary(), conversation, sources, question, rewritten);
    }
}
