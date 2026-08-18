package io.github.portfolio.rag.port;

public interface EmbeddingProvider {
    float[] embed(String text, InputType inputType);

    enum InputType { DOCUMENT, QUERY }
}
