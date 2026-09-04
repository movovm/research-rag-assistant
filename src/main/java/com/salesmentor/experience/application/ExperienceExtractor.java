package com.salesmentor.experience.application;

import com.salesmentor.experience.domain.ExperienceUnit;
import java.util.List;

public interface ExperienceExtractor {
    ExtractionBatch extract(ExtractionCommand command);

    record ExtractionCommand(Long caseId, String content, String industry,
                             com.salesmentor.salescase.domain.SalesCase.SalesStage salesStage,
                             String customerRole) {}

    record ExperienceDraft(ExperienceUnit.ScenarioType scenarioType,
                           ExperienceUnit.ObjectionType objectionType,
                           com.salesmentor.salescase.domain.SalesCase.SalesStage salesStage,
                           String customerRole, String triggerText, String strategySummary,
                           String recommendedQuestion, String evidenceQuote, String applicability) {}

    record ExtractionBatch(List<ExperienceDraft> drafts) {
        public ExtractionBatch { drafts = drafts == null ? List.of() : List.copyOf(drafts); }
    }
}
