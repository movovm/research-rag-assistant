package com.salesmentor.agent.report;

import com.salesmentor.agent.runtime.BoundedAgentRuntimeResult;
import com.salesmentor.experience.application.ExperienceSearchResult;
import com.salesmentor.experience.domain.ExperienceUnit;
import com.salesmentor.knowledge.application.ProductKnowledgeSearchResult;
import com.salesmentor.knowledge.domain.KnowledgeDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportGroundingValidatorTest {
    private final ReportGroundingValidator validator = new ReportGroundingValidator();
    private final String conversation = "Customer asks about price.\nSales explains total cost.";
    private final ExperienceSearchResult experience = new ExperienceSearchResult(7L, 70L, null, null, null, null,
            "trigger", "strategy", "question", "Sales explains total cost.", 25, 51, 0.1, 1);
    private final ProductKnowledgeSearchResult product = new ProductKnowledgeSearchResult("DOC-3", 3L,
            "Pricing", KnowledgeDocument.DocumentType.PRICING_NOTE, "synthetic-pricing-note.md", "Pricing details", 1);

    @Test void acceptsFullyGroundedReport() {
        ReviewReport report = new ReviewReport(
                List.of(new ReviewReport.ReportItem("Observed price concern", List.of("CUR-1"))),
                List.of(new ReviewReport.ReportItem("Historical strategy", List.of("EXP-7"))),
                List.of(new ReviewReport.ReportItem("Product pricing fact", List.of("DOC-3"))),
                List.of(new ReviewReport.ReportItem("Use total cost framing", List.of("CUR-2", "EXP-7"))),
                List.of("Ask about budget"),
                List.of(cur("CUR-1", "Customer asks about price.", 0, 26), cur("CUR-2", "Sales explains total cost.", 27, 53),
                        new EvidenceReference("EXP-7", EvidenceSource.HISTORICAL_EXPERIENCE, experience.evidenceQuote(), -1, -1),
                        new EvidenceReference("DOC-3", EvidenceSource.PRODUCT_KNOWLEDGE, product.excerpt(), -1, -1)),
                List.of());
        validator.validate(report, conversation, runtime());
    }

    @Test void rejectsForgedCurrentQuoteOffsetsAndBounds() {
        assertThatThrownBy(() -> validator.validate(reportWith(cur("CUR-1", "forged", 0, 26)), conversation, runtime()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(reportWith(cur("CUR-1", "x", -1, 1)), conversation, runtime()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(reportWith(cur("CUR-1", "x", 0, 999)), conversation, runtime()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsUnknownOrMismatchedHistoricalAndProductEvidence() {
        assertThatThrownBy(() -> validator.validate(reportWith(new EvidenceReference("EXP-99", EvidenceSource.HISTORICAL_EXPERIENCE, "x", -1, -1)), conversation, runtime())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(reportWith(new EvidenceReference("DOC-9", EvidenceSource.PRODUCT_KNOWLEDGE, "x", -1, -1)), conversation, runtime())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(reportWith(new EvidenceReference("DOC-3", EvidenceSource.PRODUCT_KNOWLEDGE, "wrong", -1, -1)), conversation, runtime())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void enforcesSectionOwnershipAndRecommendationEvidence() {
        EvidenceReference doc = new EvidenceReference("DOC-3", EvidenceSource.PRODUCT_KNOWLEDGE, product.excerpt(), -1, -1);
        ReviewReport wrongCurrent = new ReviewReport(List.of(new ReviewReport.ReportItem("x", List.of("DOC-3"))), List.of(), List.of(), List.of(), List.of(), List.of(doc), List.of());
        assertThatThrownBy(() -> validator.validate(wrongCurrent, conversation, runtime())).isInstanceOf(IllegalArgumentException.class);
        EvidenceReference cur = cur("CUR-1", "Customer asks about price.", 0, 26);
        ReviewReport wrongProduct = new ReviewReport(List.of(), List.of(), List.of(new ReviewReport.ReportItem("x", List.of("CUR-1"))), List.of(), List.of(), List.of(cur), List.of());
        assertThatThrownBy(() -> validator.validate(wrongProduct, conversation, runtime())).isInstanceOf(IllegalArgumentException.class);
        ReviewReport noRecommendationEvidence = new ReviewReport(List.of(), List.of(), List.of(), List.of(new ReviewReport.ReportItem("x", List.of())), List.of(), List.of(), List.of());
        assertThatThrownBy(() -> validator.validate(noRecommendationEvidence, conversation, runtime())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void reportCollectionsAreImmutable() {
        ReviewReport report = new ReviewReport(null, null, null, null, null, null, null);
        assertThatThrownBy(() -> report.references().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> report.currentObservations().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    private ReviewReport reportWith(EvidenceReference reference) {
        return new ReviewReport(List.of(new ReviewReport.ReportItem("statement", List.of(reference.referenceId()))), List.of(), List.of(), List.of(), List.of(), List.of(reference), List.of());
    }

    private EvidenceReference cur(String id, String quote, int start, int end) {
        return new EvidenceReference(id, EvidenceSource.CURRENT_CONVERSATION, quote, start, end);
    }

    private BoundedAgentRuntimeResult runtime() {
        return new BoundedAgentRuntimeResult(List.of(experience), List.of(product), List.of(), List.of());
    }
}
