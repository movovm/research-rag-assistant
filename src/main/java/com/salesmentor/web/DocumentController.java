package com.salesmentor.web;

import com.salesmentor.core.DocumentIngestionService;
import com.salesmentor.domain.DocumentSummary;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    public DocumentSummary upload(@RequestPart("file") MultipartFile file,
                                  @RequestPart(value = "documentType", required = false) String documentType,
                                  @RequestPart(value = "project", required = false) String project) throws IOException {
        return ingestion.ingest(file, valueOr(documentType, "项目资料"), valueOr(project, "科研知识库"));
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
