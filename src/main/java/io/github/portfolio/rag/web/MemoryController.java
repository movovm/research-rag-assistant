package io.github.portfolio.rag.web;

import io.github.portfolio.rag.core.LongTermMemoryService;
import io.github.portfolio.rag.domain.LongTermMemory;
import io.github.portfolio.rag.domain.MemoryRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/memories")
public class MemoryController {
    private final LongTermMemoryService service;

    public MemoryController(LongTermMemoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<LongTermMemory> list(@RequestParam String userId) {
        return service.list(userId);
    }

    @PostMapping
    public LongTermMemory create(@Valid @RequestBody MemoryRequest request) {
        return service.add(request.userId(), request.label(), request.content());
    }
}
