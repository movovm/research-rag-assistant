package com.salesmentor.port;

public interface EmbeddingProvider {
    float[] embed(String text, InputType inputType);

    enum InputType { DOCUMENT, QUERY }
}
