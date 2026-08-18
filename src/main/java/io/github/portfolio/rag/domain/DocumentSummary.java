package io.github.portfolio.rag.domain;

public record DocumentSummary(String documentId, String source, String documentType, String project, int chunks) {}
