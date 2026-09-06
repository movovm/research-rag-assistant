package com.salesmentor.knowledge.application;

import com.salesmentor.knowledge.domain.KnowledgeDocument;
import com.salesmentor.knowledge.domain.KnowledgeRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProductKnowledgeSearchApplicationServiceTest {
    @Test void admitsOnlyPublishedIndexedProductDocuments() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.findPublished()).thenReturn(List.of(
                doc(1L, KnowledgeDocument.DocumentType.PRODUCT_OVERVIEW, KnowledgeDocument.Status.PUBLISHED, KnowledgeDocument.IndexStatus.INDEXED, "Product price details"),
                doc(2L, KnowledgeDocument.DocumentType.FAQ, KnowledgeDocument.Status.DRAFT, KnowledgeDocument.IndexStatus.INDEXED, "price"),
                doc(3L, KnowledgeDocument.DocumentType.PRICING_NOTE, KnowledgeDocument.Status.PUBLISHED, KnowledgeDocument.IndexStatus.NOT_INDEXED, "price"),
                doc(4L, KnowledgeDocument.DocumentType.COMPETITOR_NOTE, KnowledgeDocument.Status.PUBLISHED, KnowledgeDocument.IndexStatus.FAILED, "price"),
                doc(5L, null, KnowledgeDocument.Status.PUBLISHED, KnowledgeDocument.IndexStatus.INDEXED, "price")));
        List<ProductKnowledgeSearchResult> results = new ProductKnowledgeSearchApplicationService(repository)
                .search(new ProductKnowledgeQuery("price", null, null, null, 10));
        assertThat(results).extracting(ProductKnowledgeSearchResult::documentId).containsExactly(1L);
        verify(repository).findPublished();
        verifyNoMoreInteractions(repository);
    }

    @Test void admitsOnlyTrustedDemoSourcesAndProductNamespaceWithPositiveId() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.findPublished()).thenReturn(List.of(
                docWith(1L, KnowledgeDocument.DocumentType.PRODUCT_OVERVIEW, "research-demo.pdf", "product", "price"),
                docWith(2L, KnowledgeDocument.DocumentType.PRODUCT_OVERVIEW, "synthetic-product-overview.md", "other", "price"),
                docWith(null, KnowledgeDocument.DocumentType.PRODUCT_OVERVIEW, "synthetic-product-overview.md", "product", "price"),
                docWith(0L, KnowledgeDocument.DocumentType.PRODUCT_OVERVIEW, "synthetic-product-overview.md", "product", "price"),
                docWith(3L, KnowledgeDocument.DocumentType.PRODUCT_OVERVIEW, "   ", "product", "price"),
                docWith(4L, KnowledgeDocument.DocumentType.PRODUCT_OVERVIEW, "synthetic-pricing-note.md", "product", "price")));
        List<ProductKnowledgeSearchResult> results = new ProductKnowledgeSearchApplicationService(repository)
                .search(new ProductKnowledgeQuery("price", null, null, null, 10));
        assertThat(results).extracting(ProductKnowledgeSearchResult::documentId).containsExactly(4L);
    }

    @Test void ranksByOverlapThenDocumentIdAndLimitsResults() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.findPublished()).thenReturn(List.of(
                doc(3L, KnowledgeDocument.DocumentType.FAQ, KnowledgeDocument.Status.PUBLISHED, KnowledgeDocument.IndexStatus.INDEXED, "price product"),
                doc(2L, KnowledgeDocument.DocumentType.PRICING_NOTE, KnowledgeDocument.Status.PUBLISHED, KnowledgeDocument.IndexStatus.INDEXED, "price"),
                doc(1L, KnowledgeDocument.DocumentType.PRODUCT_OVERVIEW, KnowledgeDocument.Status.PUBLISHED, KnowledgeDocument.IndexStatus.INDEXED, "price product")));
        List<ProductKnowledgeSearchResult> results = new ProductKnowledgeSearchApplicationService(repository)
                .search(new ProductKnowledgeQuery("price product", null, null, null, 2));
        assertThat(results).extracting(ProductKnowledgeSearchResult::documentId).containsExactly(1L, 3L);
        assertThat(results.get(0).referenceId()).isEqualTo("DOC-1");
        assertThat(results.get(0).excerpt()).isEqualTo("price product");
    }

    @Test void returnsEmptyForNoOverlapAndRejectsNullQuery() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.findPublished()).thenReturn(List.of(doc(1L, KnowledgeDocument.DocumentType.FAQ,
                KnowledgeDocument.Status.PUBLISHED, KnowledgeDocument.IndexStatus.INDEXED, "deployment")));
        ProductKnowledgeSearchApplicationService service = new ProductKnowledgeSearchApplicationService(repository);
        assertThat(service.search(new ProductKnowledgeQuery("unrelated", null, null, null, 1))).isEmpty();
        assertThatThrownBy(() -> service.search(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private KnowledgeDocument doc(Long id, KnowledgeDocument.DocumentType type, KnowledgeDocument.Status status,
                                  KnowledgeDocument.IndexStatus indexStatus, String content) {
        return docWith(id, type, "synthetic-product-overview.md", "product", content, status, indexStatus);
    }

    private KnowledgeDocument docWith(Long id, KnowledgeDocument.DocumentType type, String sourceName,
                                      String vectorNamespace, String content) {
        return docWith(id, type, sourceName, vectorNamespace, content,
                KnowledgeDocument.Status.PUBLISHED, KnowledgeDocument.IndexStatus.INDEXED);
    }

    private KnowledgeDocument docWith(Long id, KnowledgeDocument.DocumentType type, String sourceName,
                                      String vectorNamespace, String content, KnowledgeDocument.Status status,
                                      KnowledgeDocument.IndexStatus indexStatus) {
        return new KnowledgeDocument(id, "Reference title", type, sourceName, content, "hash-" + id,
                status, indexStatus, vectorNamespace, null, null);
    }
}
