# RAG Flow

## Ingestion

```mermaid
sequenceDiagram
    participant U as User
    participant API as Document API
    participant T as Apache Tika
    participant C as Semantic Chunker
    participant B as BM25 Index
    participant V as Vector Store

    U->>API: upload(file, type, project)
    API->>T: parse bytes
    T-->>API: text
    API->>C: LangChain4j Document + metadata
    C-->>API: semantic chunks
    API->>B: upsert chunks
    API->>V: embed + upsert vectors
    API-->>U: documentId + chunk count
```

## Query

```mermaid
sequenceDiagram
    participant U as User
    participant C as Chat Service
    participant S as Short Memory
    participant R as Retriever
    participant L as Long Memory
    participant A as Answer Generator

    U->>C: question
    C->>S: load session
    C->>C: query rewrite
    C->>L: semantic recall
    C->>R: BM25 + Dense
    R-->>C: normalized fusion Top-K
    C->>A: profile + conversation + evidence + question
    A-->>C: grounded answer
    C->>S: append question + answer
    C-->>U: context event + token events + done
```

## Debugging order

1. 调用 `/api/retrieval/debug`，检查期望文本块是否出现。
2. 若未出现，检查文档解析、分块、关键词、Top-K、阈值和融合权重。
3. 若已出现但回答未采用，检查 Prompt、证据数量和生成模型。
4. 用人工标注的 Question → relevant chunk 数据集计算 Recall@K、MRR 和 nDCG，禁止用主观示例代替评测。
