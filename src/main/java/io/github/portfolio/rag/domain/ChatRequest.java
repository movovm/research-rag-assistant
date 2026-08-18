package io.github.portfolio.rag.domain;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String sessionId,
        @NotBlank String userId,
        @NotBlank String question
) {}
