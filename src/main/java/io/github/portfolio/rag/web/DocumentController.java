package io.github.portfolio.rag.web;

import io.github.portfolio.rag.core.DocumentIngestionService;
import io.github.portfolio.rag.domain.DocumentSummary;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentIngestionService ingestion;

    public DocumentController(DocumentIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @GetMapping
    public List<DocumentSummary> list() {
        return ingestion.listDocuments();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return ingestion.stats();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<DocumentSummary> upload(@RequestPart("file") FilePart file,
                                        @RequestPart(value = "documentType", required = false) String documentType,
                                        @RequestPart(value = "project", required = false) String project) {
        return DataBufferUtils.join(file.content()).flatMap(buffer -> {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            DataBufferUtils.release(buffer);
            return Mono.fromCallable(() -> ingestion.ingest(bytes, file.filename(),
                            valueOr(documentType, "项目资料"), valueOr(project, "科研知识库")))
                    .subscribeOn(Schedulers.boundedElastic());
        });
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
