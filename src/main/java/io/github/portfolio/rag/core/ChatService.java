package io.github.portfolio.rag.core;

import io.github.portfolio.rag.domain.ChatMessage;
import io.github.portfolio.rag.domain.ChatRequest;
import io.github.portfolio.rag.domain.ChatResult;
import io.github.portfolio.rag.domain.LongTermMemory;
import io.github.portfolio.rag.domain.MemoryState;
import io.github.portfolio.rag.domain.ScoredChunk;
import io.github.portfolio.rag.port.AnswerGenerator;
import io.github.portfolio.rag.port.ConversationMemoryStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {
    private final ConversationMemoryStore shortMemory;
    private final LongTermMemoryService longMemory;
    private final QueryRewriteService queryRewriter;
    private final HybridRetriever retriever;
    private final PromptAssembler promptAssembler;
    private final AnswerGenerator answerGenerator;

    public ChatService(ConversationMemoryStore shortMemory, LongTermMemoryService longMemory,
                       QueryRewriteService queryRewriter, HybridRetriever retriever,
                       PromptAssembler promptAssembler, AnswerGenerator answerGenerator) {
        this.shortMemory = shortMemory;
        this.longMemory = longMemory;
        this.queryRewriter = queryRewriter;
        this.retriever = retriever;
        this.promptAssembler = promptAssembler;
        this.answerGenerator = answerGenerator;
    }

    public ChatResult chat(ChatRequest request) {
        MemoryState before = shortMemory.get(request.sessionId());
        String rewritten = queryRewriter.rewrite(request.question(), before);
        List<LongTermMemory> memories = longMemory.retrieve(request.userId(), rewritten, 3);
        List<ScoredChunk> evidence = retriever.retrieve(rewritten);
        String prompt = promptAssembler.assemble(request.question(), rewritten, before, memories, evidence);
        String answer = answerGenerator.generate(prompt, rewritten, evidence);
        shortMemory.append(request.sessionId(), ChatMessage.user(request.question()));
        MemoryState updated = shortMemory.append(request.sessionId(), ChatMessage.assistant(answer));
        return new ChatResult(answer, request.question(), rewritten, updated.summary(), memories, evidence,
                List.of("短期记忆召回", "Query Rewrite", "长期记忆召回", "BM25 + Dense 混合检索", "证据约束生成", "记忆更新"));
    }

    public void clear(String sessionId) {
        shortMemory.clear(sessionId);
    }
}
