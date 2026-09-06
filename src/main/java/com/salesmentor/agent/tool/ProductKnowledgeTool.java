package com.salesmentor.agent.tool;

import com.salesmentor.agent.model.ReviewToolName;
import com.salesmentor.knowledge.application.ProductKnowledgeQuery;
import com.salesmentor.knowledge.application.ProductKnowledgeSearchApplicationService;
import com.salesmentor.knowledge.application.ProductKnowledgeSearchResult;

import java.util.List;

public final class ProductKnowledgeTool implements ReviewReadOnlyTool {
    private final ProductKnowledgeSearchApplicationService searchService;

    public ProductKnowledgeTool(ProductKnowledgeSearchApplicationService searchService) {
        this.searchService = searchService;
    }

    @Override
    public ReviewToolName name() {
        return ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH;
    }

    public List<ProductKnowledgeSearchResult> search(ProductKnowledgeQuery query) {
        if (query == null) throw new IllegalArgumentException("query is required");
        return searchService.search(query);
    }
}
