package io.github.portfolio.rag.adapter.local;

import io.github.portfolio.rag.config.RagProperties;
import io.github.portfolio.rag.domain.ChatMessage;
import io.github.portfolio.rag.domain.MemoryState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryConversationMemoryStoreTest {
    @Test
    void compactsOldMessagesWhenBudgetIsExceeded() {
        RagProperties properties = new RagProperties("local", 5, .58, 20, 100, .1, 80, false,
                new RagProperties.Cloud("", "", "", 0, "", "", ""));
        InMemoryConversationMemoryStore store = new InMemoryConversationMemoryStore(properties);

        for (int i = 0; i < 8; i++) {
            store.append("session", ChatMessage.user("这是一条用于触发摘要压缩的对话消息-" + i));
        }
        MemoryState state = store.get("session");

        assertThat(state.summary()).isNotBlank();
        assertThat(state.messages()).hasSizeLessThan(8);
    }
}
