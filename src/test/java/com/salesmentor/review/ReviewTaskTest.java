package com.salesmentor.review;

import com.salesmentor.review.domain.ReviewTask;
import com.salesmentor.salescase.domain.SalesCase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewTaskTest {
    private final LocalDateTime now = LocalDateTime.now();

    @Test
    void rejectsMissingRequiredFieldsAndInvalidVersion() {
        assertThatThrownBy(() -> task(null, "goal")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReviewTask(null, "req", null, null, null, null, null,
                "content", "goal", ReviewTask.Status.PENDING, null, null, null, -1, null, null,
                null, null, now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> task("   ", "goal")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresGoalForStrictStatusesButKeepsLegacyPartialCompatibility() {
        assertThatThrownBy(() -> task(null, null)).isInstanceOf(IllegalArgumentException.class);
        new ReviewTask(null, "legacy", null, null, null, null, null, "content", null,
                ReviewTask.Status.PARTIAL_SUCCEEDED, null, null, null, 0, null, null,
                null, null, now, now);
    }

    @Test
    void rejectsOversizedFailureFields() {
        assertThatThrownBy(() -> new ReviewTask(null, "req", null, null, null, SalesCase.SalesStage.DISCOVERY,
                null, "content", "goal", ReviewTask.Status.PENDING, null, null, null, 0,
                "x".repeat(65), null, null, null, now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReviewTask(null, "req", null, null, null, SalesCase.SalesStage.DISCOVERY,
                null, "content", "goal", ReviewTask.Status.PENDING, null, null, null, 0,
                null, "x".repeat(501), null, null, now, now)).isInstanceOf(IllegalArgumentException.class);
    }

    private ReviewTask task(String requestId, String goal) {
        return new ReviewTask(null, requestId, null, null, null, null, null, "content", goal,
                ReviewTask.Status.PENDING, null, null, null, 0, null, null, null, null, now, now);
    }
}
