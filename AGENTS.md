# Project Context

## Purpose

This repository is migrating into SalesMentor V1: an evidence-based sales conversation review agent. Preserve the distinction between deterministic local adapters and real cloud AI adapters.

## Architecture rules

- Use a Spring Boot modular monolith under the `com.salesmentor` root package.
- Controllers call application services; domain and agent code must not call MyBatis mappers or concrete cloud clients.
- MySQL is the source of truth. BM25 and vector indexes are rebuildable derived data.
- Local adapters must run without AI credentials; cloud adapters may access DashScope, Pinecone, and optional Redis only when enabled.
- Keep retrieval evidence, metadata, ranks, fallbacks, and execution status visible through debug or trace responses.
- Long-running extraction and review tasks use bounded executors. SSE transports events but never owns task state.

## Business rules

- LLM extraction structures an existing sales case; it does not prove that a strategy is optimal or effective.
- Experience follows `GENERATED → VERIFIED → PUBLISHED`; generated output is never automatically published.
- Only `PUBLISHED + INDEXED` experiences may be retrieved.
- The Sales Review Agent has exactly two read-only tools: experience search and product-knowledge search.
- Every knowledge-backed recommendation must cite evidence returned during the current task.
- Never log or commit API keys, passwords, complete prompts, resume contact details, or unnecessary conversation content.
- Never describe deterministic Feature Hashing, rule planners, or template reports as LLM output.

## V1 exclusions

Do not add ASR, CRM, role play, multiple agents, a third tool, Kafka, Elasticsearch, MinIO, complex authorization, autonomous internet access, or unbounded ReAct loops.

## Verification

- Run `mvn verify` before merging.
- Integration tests use MySQL Testcontainers and Flyway migrations.
- For UI changes, start the application with MySQL, exercise the review flow, retrieval debug, task recovery, and trace views on desktop and mobile.
