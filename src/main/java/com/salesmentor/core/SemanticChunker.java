package com.salesmentor.core;

import dev.langchain4j.data.document.Document;
import com.salesmentor.adapter.local.InMemoryVectorStore;
import com.salesmentor.config.RagProperties;
import com.salesmentor.domain.DocumentChunk;
import com.salesmentor.port.EmbeddingProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Component
public class SemanticChunker {
    private final EmbeddingProvider embeddings;
    private final RagProperties properties;

    public SemanticChunker(EmbeddingProvider embeddings, RagProperties properties) {
        this.embeddings = embeddings;
        this.properties = properties;
    }

    public List<DocumentChunk> split(String documentId, Document document) {
        String source = document.metadata().getString("source");
        String type = document.metadata().getString("documentType");
        String project = document.metadata().getString("project");
        List<String> paragraphs = paragraphs(document.text());
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        float[] currentVector = null;

        for (String paragraph : paragraphs) {
            float[] nextVector = embeddings.embed(paragraph, EmbeddingProvider.InputType.DOCUMENT);
            boolean fits = current.length() + paragraph.length() + 2 <= properties.chunkMaxChars();
            boolean related = currentVector == null || InMemoryVectorStore.cosine(currentVector, nextVector) >= properties.semanticThreshold();
            if (current.isEmpty() || (fits && (current.length() < properties.chunkMinChars() || related))) {
                if (!current.isEmpty()) current.append("\n\n");
                current.append(paragraph);
                currentVector = embeddings.embed(current.toString(), EmbeddingProvider.InputType.DOCUMENT);
            } else {
                merged.add(current.toString());
                current = new StringBuilder(paragraph);
                currentVector = nextVector;
            }
        }
        if (!current.isEmpty()) merged.add(current.toString());

        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < merged.size(); i++) {
            String content = merged.get(i);
            String id = documentId + "-" + String.format("%03d", i + 1) + "-" + shortHash(content);
            chunks.add(new DocumentChunk(id, documentId, source, type, project, content,
                    Map.of("chunkIndex", String.valueOf(i), "source", source, "documentType", type, "project", project)));
        }
        return chunks;
    }

    private List<String> paragraphs(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String block : normalized.split("\\n\\s*\\n|(?<=[。！？；])\\s*")) {
            String clean = block.replaceAll("[ \\t]+", " ").trim();
            if (!clean.isBlank()) result.add(clean);
        }
        return result;
    }

    private String shortHash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
