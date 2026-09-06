package com.salesmentor.domain;

import jakarta.validation.constraints.NotBlank;

public record MemoryRequest(@NotBlank String userId, @NotBlank String label, @NotBlank String content) {}
