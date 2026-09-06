package com.salesmentor.experience.application;

import com.salesmentor.experience.domain.ExperienceUnit;
import com.salesmentor.salescase.domain.SalesCase;

public record ExperienceSearchResult(
        Long experienceId,
        Long caseId,
        ExperienceUnit.ScenarioType scenarioType,
        ExperienceUnit.ObjectionType objectionType,
        SalesCase.SalesStage salesStage,
        String customerRole,
        String triggerText,
        String strategySummary,
        String recommendedQuestion,
        String evidenceQuote,
        int evidenceStart,
        int evidenceEnd,
        double retrievalScore,
        int rank
) {}
