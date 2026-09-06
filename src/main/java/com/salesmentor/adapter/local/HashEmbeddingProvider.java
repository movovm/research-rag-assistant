package com.salesmentor.adapter.local;

import com.salesmentor.core.TextTokenizer;
import com.salesmentor.port.EmbeddingProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.rag", name = "mode", havingValue = "local", matchIfMissing = true)
public class HashEmbeddingProvider implements EmbeddingProvider {
    private static final int DIMENSION = 384;
    private final TextTokenizer tokenizer;

    public HashEmbeddingProvider(TextTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    public float[] embed(String text, InputType inputType) {
        float[] vector = new float[DIMENSION];
        List<String> tokens = tokenizer.tokenize(text);
        for (String token : tokens) {
            int hash = murmurLikeHash(token.getBytes(StandardCharsets.UTF_8));
            int index = Math.floorMod(hash, DIMENSION);
            vector[index] += (hash & 1) == 0 ? 1f : -1f;
        }
        normalize(vector);
        return vector;
    }

    private int murmurLikeHash(byte[] bytes) {
        int hash = 0x9747b28c;
        for (byte value : bytes) {
            hash ^= value & 0xff;
            hash *= 0x5bd1e995;
            hash ^= hash >>> 15;
        }
        return hash;
    }

    private void normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) return;
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= (float) norm;
        }
    }
}
