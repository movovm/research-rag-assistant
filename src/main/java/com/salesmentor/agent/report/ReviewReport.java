package com.salesmentor.agent.report;

import java.util.List;

public record ReviewReport(
        List<ReportItem> currentObservations,
        List<ReportItem> historicalExperiences,
        List<ReportItem> productFacts,
        List<ReportItem> recommendations,
        List<String> nextQuestions,
        List<EvidenceReference> references,
        List<String> limitations
) {
    public ReviewReport {
        currentObservations = immutable(currentObservations);
        historicalExperiences = immutable(historicalExperiences);
        productFacts = immutable(productFacts);
        recommendations = immutable(recommendations);
        nextQuestions = immutable(nextQuestions);
        references = immutable(references);
        limitations = immutable(limitations);
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record ReportItem(String statement, List<String> evidenceIds) {
        public ReportItem {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }
}
