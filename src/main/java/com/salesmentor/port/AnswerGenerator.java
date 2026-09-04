package com.salesmentor.port;

import com.salesmentor.domain.ScoredChunk;

import java.util.List;

public interface AnswerGenerator {
    String generate(String prompt, String question, List<ScoredChunk> evidence);
}
