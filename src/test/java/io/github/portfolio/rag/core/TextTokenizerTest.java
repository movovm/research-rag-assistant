package io.github.portfolio.rag.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextTokenizerTest {
    private final TextTokenizer tokenizer = new TextTokenizer();

    @Test
    void tokenizesChineseBigramsAndTechnicalTerms() {
        assertThat(tokenizer.tokenize("Redis 缓存穿透 + JVM"))
                .contains("redis", "缓", "存", "缓存", "穿透", "jvm");
    }
}
