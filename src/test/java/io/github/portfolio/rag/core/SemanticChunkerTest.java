package io.github.portfolio.rag.core;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import io.github.portfolio.rag.adapter.local.HashEmbeddingProvider;
import io.github.portfolio.rag.config.RagProperties;
import io.github.portfolio.rag.domain.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticChunkerTest {
    @Test
    void preservesMetadataAndChunkLimits() {
        RagProperties properties = new RagProperties("local", 5, .58, 40, 120, .12, 1000, false,
                new RagProperties.Cloud("", "", "", 0, "", "", ""));
        TextTokenizer tokenizer = new TextTokenizer();
        SemanticChunker chunker = new SemanticChunker(new HashEmbeddingProvider(tokenizer), properties);
        Metadata metadata = Metadata.from(Map.of("source", "guide.md", "documentType", "规范", "project", "RAG"));
        Document document = Document.from("缓存穿透需要缓存空值。缓存空值要设置较短 TTL。\n\n" +
                "混合检索同时使用关键词匹配和向量检索。两路分数归一化后再进行融合。\n\n" +
                "单元测试需要覆盖正常路径、边界条件和异常分支。", metadata);

        List<DocumentChunk> chunks = chunker.split("doc-1", document);

        assertThat(chunks).isNotEmpty().allSatisfy(chunk -> {
            assertThat(chunk.source()).isEqualTo("guide.md");
            assertThat(chunk.documentType()).isEqualTo("规范");
            assertThat(chunk.content()).isNotBlank();
        });
    }
}
