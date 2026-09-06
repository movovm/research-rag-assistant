package com.salesmentor.knowledge.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.salesmentor.knowledge.domain.KnowledgeDocument;

import java.time.LocalDateTime;

@TableName("sm_knowledge_document")
public class KnowledgeDocumentEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String title;
    private String documentType;
    private String sourceName;
    private String content;
    private String contentHash;
    private String status;
    private String indexStatus;
    private String vectorNamespace;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static KnowledgeDocumentEntity fromDomain(KnowledgeDocument value) {
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.id = value.id();
        entity.title = value.title();
        entity.documentType = value.documentType().name();
        entity.sourceName = value.sourceName();
        entity.content = value.content();
        entity.contentHash = value.contentHash();
        entity.status = value.status().name();
        entity.indexStatus = value.indexStatus().name();
        entity.vectorNamespace = value.vectorNamespace();
        entity.createdAt = value.createdAt();
        entity.updatedAt = value.updatedAt();
        return entity;
    }

    public KnowledgeDocument toDomain() {
        return new KnowledgeDocument(id, title, KnowledgeDocument.DocumentType.valueOf(documentType), sourceName,
                content, contentHash, KnowledgeDocument.Status.valueOf(status),
                KnowledgeDocument.IndexStatus.valueOf(indexStatus), vectorNamespace, createdAt, updatedAt);
    }
}
