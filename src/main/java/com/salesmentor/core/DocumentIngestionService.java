package com.salesmentor.core;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import com.salesmentor.domain.ChunkVector;
import com.salesmentor.domain.DocumentChunk;
import com.salesmentor.domain.DocumentSummary;
import com.salesmentor.port.EmbeddingProvider;
import com.salesmentor.port.VectorStore;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentIngestionService {
    private final Tika tika = new Tika();
    private final SemanticChunker chunker;
    private final EmbeddingProvider embeddings;
    private final VectorStore vectorStore;
    private final LexicalIndex lexicalIndex;
    private final Map<String, DocumentSummary> documents = new ConcurrentHashMap<>();

    public DocumentIngestionService(SemanticChunker chunker, EmbeddingProvider embeddings,
                                    VectorStore vectorStore, LexicalIndex lexicalIndex) {
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.vectorStore = vectorStore;
        this.lexicalIndex = lexicalIndex;
        tika.setMaxStringLength(2_000_000);
    }

    public DocumentSummary ingest(MultipartFile file, String type, String project) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("上传文件不能为空");
        if (file.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException("文件不能超过 10MB");
        String filename = sanitize(file.getOriginalFilename());
        try (InputStream input = file.getInputStream()) {
            String text = tika.parseToString(input);
            return ingestText(filename, type, project, text);
        } catch (Exception e) {
            if (e instanceof IOException ioException) throw ioException;
            throw new IllegalArgumentException("文档解析失败：" + e.getMessage(), e);
        }
    }

    public DocumentSummary ingest(byte[] bytes, String filename, String type, String project) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("上传文件不能为空");
        if (bytes.length > 10 * 1024 * 1024) throw new IllegalArgumentException("文件不能超过 10MB");
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            return ingestText(sanitize(filename), type, project, tika.parseToString(input));
        } catch (Exception e) {
            throw new IllegalArgumentException("文档解析失败：" + e.getMessage(), e);
        }
    }

    public DocumentSummary ingestText(String source, String type, String project, String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("文档中没有可索引文本");
        String documentId = shortHash(source + "\n" + text);
        Metadata metadata = Metadata.from(Map.of("source", source, "documentType", type, "project", project));
        Document document = Document.from(text, metadata);
        List<DocumentChunk> chunks = chunker.split(documentId, document);
        List<ChunkVector> vectors = chunks.stream()
                .map(chunk -> new ChunkVector(chunk, embeddings.embed(chunk.content(), EmbeddingProvider.InputType.DOCUMENT)))
                .toList();
        vectorStore.upsert(vectors);
        lexicalIndex.upsert(chunks);
        DocumentSummary summary = new DocumentSummary(documentId, source, type, project, chunks.size());
        documents.put(documentId, summary);
        return summary;
    }

    public List<DocumentSummary> listDocuments() {
        return documents.values().stream().sorted(Comparator.comparing(DocumentSummary::source)).toList();
    }

    public Map<String, Object> stats() {
        return Map.of("documents", documents.size(), "chunks", vectorStore.count(), "mode", "ready");
    }

    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) return "uploaded-document";
        return filename.replaceAll("[\\r\\n\\\\/]", "_");
    }

    private String shortHash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
