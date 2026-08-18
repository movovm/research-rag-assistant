# Project Context

## Purpose

This repository is a public, runnable portfolio for a research-document RAG assistant. Preserve the distinction between local demo behavior and cloud production adapters.

## Architecture rules

- `core` owns business orchestration and retrieval logic.
- `port` defines infrastructure boundaries.
- `adapter/local` must run without credentials or external services.
- `adapter/cloud` may access DashScope, Pinecone, and Redis only when `app.rag.mode=cloud`.
- `web` exposes REST and SSE; controllers should not implement retrieval logic.
- Keep retrieval evidence and scores visible in API responses for debugging.

## Business rules

- Long-term memory is created only by explicit user action.
- Preserve the original question even when Query Rewrite is applied.
- Answers must be grounded in retrieved evidence; return an insufficiency message when evidence is empty.
- Never log or commit API keys, tokens, passwords, resume contact details, or absolute personal paths.
- Do not describe the deterministic local hash embedding as an LLM embedding model.

## Verification

Run `mvn test` before merging. For UI changes, start with `mvn spring-boot:run`, exercise chat, retrieval debug, document upload, and check desktop plus mobile layouts.
