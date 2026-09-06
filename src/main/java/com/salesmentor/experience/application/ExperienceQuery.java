package com.salesmentor.experience.application;

import com.salesmentor.experience.domain.ExperienceUnit;
import com.salesmentor.salescase.domain.SalesCase;

public record ExperienceQuery(
        String queryText,
        ExperienceUnit.ScenarioType scenarioType,
        ExperienceUnit.ObjectionType objectionType,
        SalesCase.SalesStage salesStage,
        String customerRole,
        int topK
) {
    public ExperienceQuery {
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("queryText is required");
        }
        if (topK < 1 || topK > 20) {
            throw new IllegalArgumentException("topK must be between 1 and 20");
        }
    }
}
