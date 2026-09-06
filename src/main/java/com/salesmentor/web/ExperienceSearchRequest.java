package com.salesmentor.web;

import com.salesmentor.experience.application.ExperienceQuery;
import com.salesmentor.experience.domain.ExperienceUnit;
import com.salesmentor.salescase.domain.SalesCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ExperienceSearchRequest(
        @NotBlank String queryText,
        ExperienceUnit.ScenarioType scenarioType,
        ExperienceUnit.ObjectionType objectionType,
        SalesCase.SalesStage salesStage,
        String customerRole,
        @Min(1) @Max(20) Integer topK
) {
    public ExperienceQuery toQuery() {
        return new ExperienceQuery(queryText, scenarioType, objectionType, salesStage,
                customerRole == null || customerRole.isBlank() ? null : customerRole,
                topK == null ? 5 : topK);
    }
}
