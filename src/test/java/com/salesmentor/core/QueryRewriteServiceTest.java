package com.salesmentor.core;

import com.salesmentor.domain.ChatMessage;
import com.salesmentor.domain.MemoryState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryRewriteServiceTest {
    private final QueryRewriteService service = new QueryRewriteService();

    @Test
    void rewritesContextDependentFollowUp() {
        MemoryState memory = new MemoryState("", List.of(
                ChatMessage.user("Redis 缓存穿透如何处理？"),
                ChatMessage.assistant("可以缓存空值。")
        ));

        String result = service.rewrite("这个方案有什么风险？", memory);

        assertThat(result).isEqualTo("Redis 缓存穿透如何处理？；用户追问：这个方案有什么风险？");
    }

    @Test
    void keepsIndependentQuestionUnchanged() {
        assertThat(service.rewrite("BM25 的作用是什么？", MemoryState.empty()))
                .isEqualTo("BM25 的作用是什么？");
    }
}
