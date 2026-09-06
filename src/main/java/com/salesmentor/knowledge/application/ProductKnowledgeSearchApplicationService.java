package com.salesmentor.knowledge.application;

import com.salesmentor.knowledge.domain.KnowledgeDocument;
import com.salesmentor.knowledge.domain.KnowledgeRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProductKnowledgeSearchApplicationService {
    private static final Set<String> TRUSTED_DEMO_PRODUCT_SOURCES = Set.of(
            "synthetic-product-overview.md",
            "synthetic-pricing-note.md",
            "synthetic-deployment-guide.md");
    private static final Set<KnowledgeDocument.DocumentType> PRODUCT_TYPES = Set.of(
            KnowledgeDocument.DocumentType.PRODUCT_OVERVIEW,
            KnowledgeDocument.DocumentType.FAQ,
            KnowledgeDocument.DocumentType.PRICING_NOTE,
            KnowledgeDocument.DocumentType.COMPETITOR_NOTE,
            KnowledgeDocument.DocumentType.DEPLOYMENT_GUIDE);
    private static final Pattern ENGLISH_WORD = Pattern.compile("[A-Za-z0-9]+");

    private final KnowledgeRepository knowledge;

    public ProductKnowledgeSearchApplicationService(KnowledgeRepository knowledge) {
        this.knowledge = knowledge;
    }

    public List<ProductKnowledgeSearchResult> search(ProductKnowledgeQuery query) {
        if (query == null) throw new IllegalArgumentException("query is required");
        Set<String> queryTerms = terms(query.queryText());
        return knowledge.findPublished().stream()
                .filter(this::isAdmittedProduct)
                .map(document -> scored(document, queryTerms))
                .filter(value -> value.score() > 0)
                .sorted(Comparator.comparingInt(ProductKnowledgeSearchResult::score).reversed()
                        .thenComparing(ProductKnowledgeSearchResult::documentId))
                .limit(query.topK())
                .toList();
    }

    private boolean isAdmittedProduct(KnowledgeDocument document) {
        return document != null
                && document.id() != null && document.id() > 0
                && document.status() == KnowledgeDocument.Status.PUBLISHED
                && document.indexStatus() == KnowledgeDocument.IndexStatus.INDEXED
                && document.documentType() != null
                && PRODUCT_TYPES.contains(document.documentType())
                && document.sourceName() != null && !document.sourceName().isBlank()
                && TRUSTED_DEMO_PRODUCT_SOURCES.contains(document.sourceName())
                && "product".equals(document.vectorNamespace());
    }

    private ProductKnowledgeSearchResult scored(KnowledgeDocument document, Set<String> queryTerms) {
        Set<String> documentTerms = terms((document.title() == null ? "" : document.title()) + " "
                + (document.content() == null ? "" : document.content()));
        documentTerms.retainAll(queryTerms);
        return new ProductKnowledgeSearchResult("DOC-" + document.id(), document.id(), document.title(),
                document.documentType(), document.sourceName(), excerpt(document.content(), documentTerms),
                documentTerms.size());
    }

    private String excerpt(String content, Set<String> matchingTerms) {
        if (content == null || content.isEmpty()) return "";
        if (matchingTerms.isEmpty() || content.length() <= 240) return content;
        String lower = content.toLowerCase(Locale.ROOT);
        int position = matchingTerms.stream().mapToInt(lower::indexOf).filter(value -> value >= 0).min().orElse(0);
        int start = Math.max(0, position - 80);
        return content.substring(start, Math.min(content.length(), start + 240));
    }

    private Set<String> terms(String text) {
        Set<String> result = new HashSet<>();
        if (text == null) return result;
        Matcher matcher = ENGLISH_WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) result.add(matcher.group());
        text.codePoints().filter(Character::isIdeographic)
                .mapToObj(Character::toString).forEach(result::add);
        return result;
    }
}
