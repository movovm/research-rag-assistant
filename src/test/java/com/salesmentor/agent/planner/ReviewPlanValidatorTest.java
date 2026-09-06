package com.salesmentor.agent.planner;

import com.salesmentor.agent.model.ReviewPlan;
import com.salesmentor.agent.model.ReviewToolName;
import com.salesmentor.experience.application.ExperienceQuery;
import com.salesmentor.knowledge.application.ProductKnowledgeQuery;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewPlanValidatorTest {
    @Test void rejectsMismatchedQueriesAndReasons() {
        ExperienceQuery experience = new ExperienceQuery("q", null, null, null, null, 1);
        assertThatThrownBy(() -> new ReviewPlan(Set.of(ReviewToolName.EXPERIENCE_SEARCH), null, null,
                Map.of(ReviewToolName.EXPERIENCE_SEARCH, "why"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReviewPlan(Set.of(), experience, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReviewPlan(Set.of(ReviewToolName.EXPERIENCE_SEARCH), experience, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReviewPlan(Set.of(ReviewToolName.EXPERIENCE_SEARCH), experience, null,
                Map.of(ReviewToolName.EXPERIENCE_SEARCH, "   ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReviewPlan(Set.of(), null, null,
                Map.of(ReviewToolName.EXPERIENCE_SEARCH, "why"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsInvalidProductQueryAndDuplicateSetInput() {
        assertThatThrownBy(() -> new ProductKnowledgeQuery(" ", null, null, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductKnowledgeQuery("q", null, null, null, 11))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Set.of(ReviewToolName.EXPERIENCE_SEARCH, ReviewToolName.EXPERIENCE_SEARCH))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
