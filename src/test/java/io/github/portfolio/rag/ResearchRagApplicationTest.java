package io.github.portfolio.rag;

import io.github.portfolio.rag.core.ChatService;
import io.github.portfolio.rag.core.DocumentIngestionService;
import io.github.portfolio.rag.domain.ChatRequest;
import io.github.portfolio.rag.domain.ChatResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ResearchRagApplicationTest {
    @Autowired
    private ChatService chatService;

    @Autowired
    private DocumentIngestionService ingestion;

    @Test
    void completesEndToEndLocalRagFlow() {
        ingestion.ingestText("test-guide.md", "研发规范", "demo",
                "缓存穿透是请求不存在的数据。可以缓存空值，并给空值设置较短 TTL。布隆过滤器适用于大规模主键集合。");

        ChatResult result = chatService.chat(new ChatRequest("test-session", "test-user", "缓存穿透怎么处理？"));

        assertThat(result.answer()).contains("缓存");
        assertThat(result.evidence()).isNotEmpty();
        assertThat(result.stages()).contains("Query Rewrite", "BM25 + Dense 混合检索");
    }
}
