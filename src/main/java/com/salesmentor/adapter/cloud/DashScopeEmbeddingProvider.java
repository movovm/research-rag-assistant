package com.salesmentor.adapter.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmentor.config.RagProperties;
import com.salesmentor.port.EmbeddingProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.rag", name = "mode", havingValue = "cloud")
public class DashScopeEmbeddingProvider implements EmbeddingProvider {
    private final RagProperties.Cloud config;
    private final RestClient client;

    public DashScopeEmbeddingProvider(RagProperties properties, RestClient.Builder builder) {
        this.config = properties.cloud();
        require(config.dashscopeApiKey(), "DASHSCOPE_API_KEY");
        this.client = builder.baseUrl("https://dashscope.aliyuncs.com").build();
    }

    @Override
    public float[] embed(String text, InputType inputType) {
        Map<String, Object> body = Map.of(
                "model", config.embeddingModel(),
                "input", Map.of("texts", new String[]{text}),
                "parameters", Map.of(
                        "dimension", config.embeddingDimension(),
                        "text_type", inputType == InputType.QUERY ? "query" : "document"
                )
        );
        JsonNode response = client.post()
                .uri("/api/v1/services/embeddings/text-embedding/text-embedding")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + config.dashscopeApiKey())
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        JsonNode values = response.path("output").path("embeddings").path(0).path("embedding");
        if (!values.isArray()) throw new IllegalStateException("DashScope embedding response is missing values");
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) vector[i] = values.get(i).floatValue();
        return vector;
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required in cloud mode");
    }
}
