package com.salesmentor.agent.report;

import com.salesmentor.agent.runtime.BoundedAgentRuntimeResult;
import com.salesmentor.experience.application.ExperienceSearchResult;
import com.salesmentor.knowledge.application.ProductKnowledgeSearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ReportGroundingValidator {
    public void validate(ReviewReport report, String conversationContent, BoundedAgentRuntimeResult runtimeResult) {
        if (report == null || conversationContent == null || runtimeResult == null) {
            throw new IllegalArgumentException("report grounding input is required");
        }
        Map<String, EvidenceReference> references = new HashMap<>();
        for (EvidenceReference reference : report.references()) validateReference(reference, conversationContent, runtimeResult, references);
        validateItems(report.currentObservations(), references, EvidenceSource.CURRENT_CONVERSATION, false);
        validateItems(report.historicalExperiences(), references, EvidenceSource.HISTORICAL_EXPERIENCE, false);
        validateItems(report.productFacts(), references, EvidenceSource.PRODUCT_KNOWLEDGE, false);
        validateItems(report.recommendations(), references, null, true);
    }

    private void validateReference(EvidenceReference reference, String content, BoundedAgentRuntimeResult runtime,
                                   Map<String, EvidenceReference> references) {
        if (reference == null || reference.referenceId() == null || reference.source() == null
                || reference.quote() == null || !referencesUnique(references, reference)) {
            throw new IllegalArgumentException("invalid evidence reference");
        }
        String id = reference.referenceId();
        if (id.startsWith("CUR-")) {
            if (reference.source() != EvidenceSource.CURRENT_CONVERSATION || reference.startOffset() < 0
                    || reference.endOffset() < reference.startOffset() || reference.endOffset() > content.length()
                    || !reference.quote().equals(content.substring(reference.startOffset(), reference.endOffset()))) {
                throw new IllegalArgumentException("invalid current conversation evidence");
            }
        } else if (id.startsWith("EXP-")) {
            if (reference.source() != EvidenceSource.HISTORICAL_EXPERIENCE
                    || !experienceMatches(id, reference.quote(), runtime)) {
                throw new IllegalArgumentException("invalid historical evidence");
            }
        } else if (id.startsWith("DOC-")) {
            if (reference.source() != EvidenceSource.PRODUCT_KNOWLEDGE
                    || !productMatches(id, reference.quote(), runtime)) {
                throw new IllegalArgumentException("invalid product evidence");
            }
        } else {
            throw new IllegalArgumentException("invalid evidence reference id");
        }
        references.put(id, reference);
    }

    private boolean referencesUnique(Map<String, EvidenceReference> references, EvidenceReference reference) {
        return !references.containsKey(reference.referenceId());
    }

    private boolean experienceMatches(String id, String quote, BoundedAgentRuntimeResult runtime) {
        for (ExperienceSearchResult result : runtime.experienceResults()) {
            if (("EXP-" + result.experienceId()).equals(id) && quote.equals(result.evidenceQuote())) return true;
        }
        return false;
    }

    private boolean productMatches(String id, String quote, BoundedAgentRuntimeResult runtime) {
        for (ProductKnowledgeSearchResult result : runtime.productKnowledgeResults()) {
            if (id.equals(result.referenceId()) && quote.equals(result.excerpt())) return true;
        }
        return false;
    }

    private void validateItems(java.util.List<ReviewReport.ReportItem> items, Map<String, EvidenceReference> references,
                               EvidenceSource requiredSource, boolean recommendation) {
        for (ReviewReport.ReportItem item : items) {
            if (item == null || item.statement() == null || item.statement().isBlank()
                    || (recommendation && item.evidenceIds().isEmpty())) {
                throw new IllegalArgumentException("invalid report item");
            }
            Set<String> ids = new HashSet<>(item.evidenceIds());
            if (ids.size() != item.evidenceIds().size()) throw new IllegalArgumentException("duplicate evidence id");
            for (String id : ids) {
                EvidenceReference reference = references.get(id);
                if (reference == null || (requiredSource != null && reference.source() != requiredSource)) {
                    throw new IllegalArgumentException("evidence does not belong to report section");
                }
                if (requiredSource == null && reference.source() == null) throw new IllegalArgumentException("invalid evidence source");
            }
        }
    }
}
