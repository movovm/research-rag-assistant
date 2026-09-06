package com.salesmentor.knowledge.domain;

import java.util.List;
import java.util.Optional;

public interface KnowledgeRepository {
    KnowledgeDocument save(KnowledgeDocument document);

    Optional<KnowledgeDocument> findById(Long id);

    List<KnowledgeDocument> findPublished();
}
