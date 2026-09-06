package com.salesmentor.agent.tool;

import com.salesmentor.agent.model.ReviewToolName;
import com.salesmentor.knowledge.application.ProductKnowledgeQuery;
import com.salesmentor.knowledge.application.ProductKnowledgeSearchApplicationService;
import com.salesmentor.knowledge.application.ProductKnowledgeSearchResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProductKnowledgeToolTest {
    @Test void delegatesSameQueryAndKeepsResults() {
        ProductKnowledgeSearchApplicationService service = mock(ProductKnowledgeSearchApplicationService.class);
        ProductKnowledgeTool tool = new ProductKnowledgeTool(service);
        ProductKnowledgeQuery query = new ProductKnowledgeQuery("price", null, null, null, 1);
        List<ProductKnowledgeSearchResult> expectedResults = new ArrayList<>();
        when(service.search(query)).thenReturn(expectedResults);
        assertThat(tool.name()).isEqualTo(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH);
        assertThat(tool.search(query)).isSameAs(expectedResults);
        verify(service).search(query);
    }

    @Test void rejectsNullQuery() {
        ProductKnowledgeSearchApplicationService service = mock(ProductKnowledgeSearchApplicationService.class);
        assertThatThrownBy(() -> new ProductKnowledgeTool(service).search(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(service);
    }
}
