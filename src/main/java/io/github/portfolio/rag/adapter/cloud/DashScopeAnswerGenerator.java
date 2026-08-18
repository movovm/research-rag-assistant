package io.github.portfolio.rag.adapter.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.portfolio.rag.config.RagProperties;
import io.github.portfolio.rag.domain.ScoredChunk;
import io.github.portfolio.rag.port.AnswerGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.rag", name = "mode", havingValue = "cloud")
public class DashScopeAnswerGenerator implements AnswerGenerator {
    private final RagProperties.Cloud config;
    private final RestClient client;

    public DashScopeAnswerGenerator(RagProperties properties, RestClient.Builder builder) {
        this.config = properties.cloud();
        this.client = builder.baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1").build();
    }

    @Override
    public String generate(String prompt, String question, List<ScoredChunk> evidence) {
        JsonNode response = client.post().uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + config.dashscopeApiKey())
                .body(Map.of(
                        "model", config.chatModel(),
                        "temperature", 0.2,
                        "messages", List.of(
                                Map.of("role", "system", "content", "你是科研团队知识库助手。只能依据给定资料回答；证据不足时明确说明。"),
                                Map.of("role", "user", "content", prompt)
                        )))
                .retrieve().body(JsonNode.class);
        String answer = response.path("choices").path(0).path("message").path("content").asText();
        if (answer.isBlank()) throw new IllegalStateException("DashScope chat response is empty");
        return answer;
    }
}
