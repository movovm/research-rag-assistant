package io.github.portfolio.rag.core;

import io.github.portfolio.rag.adapter.local.HashEmbeddingProvider;
import io.github.portfolio.rag.adapter.local.InMemoryVectorStore;
import io.github.portfolio.rag.config.RagProperties;
import io.github.portfolio.rag.domain.ChunkVector;
import io.github.portfolio.rag.domain.DocumentChunk;
import io.github.portfolio.rag.domain.ScoredChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HybridRetrieverTest {
    @Test
    void ranksExactTechnicalMatchFirst() {
        TextTokenizer tokenizer = new TextTokenizer();
        HashEmbeddingProvider embeddings = new HashEmbeddingProvider(tokenizer);
        LexicalIndex lexical = new LexicalIndex(tokenizer);
        InMemoryVectorStore vectors = new InMemoryVectorStore();
        RagProperties properties = new RagProperties("local", 3, .58, 50, 200, .1, 1000, false,
                new RagProperties.Cloud("", "", "", 0, "", "", ""));
        DocumentChunk exact = chunk("1", "Redis 缓存穿透使用空值缓存和布隆过滤器处理");
        DocumentChunk other = chunk("2", "数据库事务通过日志保证持久性与一致性");
        List<DocumentChunk> chunks = List.of(exact, other);
        lexical.upsert(chunks);
        vectors.upsert(chunks.stream().map(item -> new ChunkVector(item, embeddings.embed(item.content(),
                io.github.portfolio.rag.port.EmbeddingProvider.InputType.DOCUMENT))).toList());
        HybridRetriever retriever = new HybridRetriever(lexical, vectors, embeddings, properties);

        List<ScoredChunk> results = retriever.retrieve("Redis 缓存穿透");

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).chunk().id()).isEqualTo("1");
        assertThat(results.get(0).bm25Score()).isPositive();
        assertThat(results.get(0).combinedScore()).isPositive();
    }

    private DocumentChunk chunk(String id, String content) {
        return new DocumentChunk(id, "doc", "test.md", "规范", "demo", content, Map.of());
    }
}
