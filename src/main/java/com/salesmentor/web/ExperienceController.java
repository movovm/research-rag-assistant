package com.salesmentor.web;

import com.salesmentor.experience.application.ExperienceReviewApplicationService;
import com.salesmentor.experience.domain.ExperienceUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/experiences")
public class ExperienceController {
    private final ExperienceReviewApplicationService service;

    public ExperienceController(ExperienceReviewApplicationService service) {
        this.service = service;
    }

    @PostMapping("/{id}/review:verify")
    public ResponseEntity<ExperienceUnit> verify(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(service.verify(id, request.reviewedBy()));
    }

    @PostMapping("/{id}/review:reject")
    public ResponseEntity<ExperienceUnit> reject(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(service.reject(id, request.reviewedBy()));
    }

    public record ReviewRequest(@NotNull @Positive Long reviewedBy) {}
}
