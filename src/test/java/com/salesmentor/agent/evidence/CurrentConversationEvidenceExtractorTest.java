package com.salesmentor.agent.evidence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentConversationEvidenceExtractorTest {
    @Test void extractsNonBlankLinesWithExactOffsets() {
        String content = "customer asks about price\n\nSales proposes a comparison\n  ";
        var results = new CurrentConversationEvidenceExtractor().extract(content);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).referenceId()).isEqualTo("CUR-1");
        assertThat(content.substring(results.get(0).startOffset(), results.get(0).endOffset()))
                .isEqualTo(results.get(0).quote());
        assertThat(results.get(1).referenceId()).isEqualTo("CUR-2");
        assertThat(content.substring(results.get(1).startOffset(), results.get(1).endOffset()))
                .isEqualTo(results.get(1).quote());
        assertThatThrownBy(() -> results.add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void rejectsNullConversation() {
        assertThatThrownBy(() -> new CurrentConversationEvidenceExtractor().extract(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
