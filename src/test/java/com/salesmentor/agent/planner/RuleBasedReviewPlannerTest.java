package com.salesmentor.agent.planner;

import com.salesmentor.agent.model.ReviewInput;
import com.salesmentor.agent.model.ReviewToolName;
import com.salesmentor.salescase.domain.SalesCase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleBasedReviewPlannerTest {
    private final RuleBasedReviewPlanner planner = new RuleBasedReviewPlanner();

    @Test void selectsExperienceOnly() {
        var plan = planner.plan(input("Customer raises a price objection"));
        assertThat(plan.selectedTools()).containsExactly(ReviewToolName.EXPERIENCE_SEARCH);
        assertThat(plan.experienceQuery().salesStage()).isEqualTo(SalesCase.SalesStage.NEGOTIATION);
        assertThat(plan.experienceQuery().customerRole()).isEqualTo("buyer");
        assertThat(plan.productKnowledgeQuery()).isNull();
    }

    @Test void selectsProductOnly() {
        var plan = planner.plan(input("Explain product deployment capability and specification"));
        assertThat(plan.selectedTools()).containsExactly(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH);
        assertThat(plan.productKnowledgeQuery()).isNotNull();
        assertThat(plan.experienceQuery()).isNull();
    }

    @Test void selectsBothOrNeither() {
        assertThat(planner.plan(input("price objection and product deployment" )).selectedTools())
                .containsExactlyInAnyOrder(ReviewToolName.EXPERIENCE_SEARCH, ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH);
        assertThat(planner.plan(input("general follow up" )).selectedTools()).isEmpty();
    }

    @Test void treatsPriceExplanationAsProductFactOnly() {
        assertThat(planner.plan(input("price explanation" )).selectedTools())
                .containsExactly(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH);
        assertThat(planner.plan(input("价格说明" )).selectedTools())
                .containsExactly(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH);
    }

    @Test void combinesPriceExplanationWithIndependentObjectionSignal() {
        assertThat(planner.plan(input("pricing explanation and customer objection" )).selectedTools())
                .containsExactlyInAnyOrder(ReviewToolName.EXPERIENCE_SEARCH, ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH);
    }

    @Test void validatesInputAndWhiteListIsClosed() {
        assertThatThrownBy(() -> planner.plan(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ReviewToolName.values()).containsExactly(ReviewToolName.EXPERIENCE_SEARCH,
                ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH);
    }

    @Test void validatesReviewInputRequiredFieldsAndLimits() {
        assertThatThrownBy(() -> new ReviewInput(" ", null, null, null, "conversation", "goal"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReviewInput("request", null, null, null, " ", "goal"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReviewInput("request", null, null, null, "conversation", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReviewInput("request", null, null, null, "x".repeat(20_001), "goal"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReviewInput("request", null, null, null, "conversation", "x".repeat(2_001)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ReviewInput input(String content) {
        return new ReviewInput("req-1", "software", SalesCase.SalesStage.NEGOTIATION,
                "buyer", content, "plan next response");
    }
}
