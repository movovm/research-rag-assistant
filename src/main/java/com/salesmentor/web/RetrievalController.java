package com.salesmentor.web;

import com.salesmentor.core.HybridRetriever;
import com.salesmentor.domain.ScoredChunk;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/retrieval")
public class RetrievalController {
    private final HybridRetriever retriever;

    public RetrievalController(HybridRetriever retriever) {
        this.retriever = retriever;
    }

    @GetMapping("/debug")
    public List<ScoredChunk> debug(@RequestParam String query) {
        return retriever.retrieve(query);
    }
}
