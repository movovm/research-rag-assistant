package io.github.portfolio.rag.port;

import io.github.portfolio.rag.domain.ScoredChunk;

import java.util.List;

public interface AnswerGenerator {
    String generate(String prompt, String question, List<ScoredChunk> evidence);
}
