package com.salesmentor.knowledge.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.salesmentor.knowledge.domain.KnowledgeDocument;
import com.salesmentor.knowledge.domain.KnowledgeRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MybatisKnowledgeRepository implements KnowledgeRepository {
    private final KnowledgeDocumentMapper mapper;

    public MybatisKnowledgeRepository(KnowledgeDocumentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public KnowledgeDocument save(KnowledgeDocument value) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeDocument prepared = new KnowledgeDocument(value.id(), value.title(), value.documentType(),
                value.sourceName(), value.content(), value.contentHash(), value.status(), value.indexStatus(),
                value.vectorNamespace(), value.createdAt() == null ? now : value.createdAt(), now);
        KnowledgeDocumentEntity entity = KnowledgeDocumentEntity.fromDomain(prepared);
        if (prepared.id() == null) mapper.insert(entity);
        else mapper.updateById(entity);
        return entity.toDomain();
    }

    @Override
    public Optional<KnowledgeDocument> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(KnowledgeDocumentEntity::toDomain);
    }

    @Override
    public List<KnowledgeDocument> findPublished() {
        QueryWrapper<KnowledgeDocumentEntity> query = new QueryWrapper<>();
        query.eq("status", KnowledgeDocument.Status.PUBLISHED.name())
                .eq("index_status", KnowledgeDocument.IndexStatus.INDEXED.name())
                .orderByAsc("id");
        return mapper.selectList(query).stream().map(KnowledgeDocumentEntity::toDomain).toList();
    }
}
