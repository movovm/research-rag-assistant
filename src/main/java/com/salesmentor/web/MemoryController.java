package com.salesmentor.web;

import com.salesmentor.core.LongTermMemoryService;
import com.salesmentor.domain.LongTermMemory;
import com.salesmentor.domain.MemoryRequest;
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
