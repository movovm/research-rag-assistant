package com.salesmentor.experience;

import com.salesmentor.experience.application.ExperienceReviewApplicationService;
import com.salesmentor.experience.domain.ExperienceRepository;
import com.salesmentor.experience.domain.ExperienceStateConflictException;
import com.salesmentor.experience.domain.ExperienceUnit;
import com.salesmentor.salescase.domain.SalesCase;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExperienceReviewApplicationServiceTest {
    @Test
    void mapsReviewCasLossToExperienceStateConflict() {
        ExperienceRepository experiences = mock(ExperienceRepository.class);
        ExperienceReviewApplicationService service = new ExperienceReviewApplicationService(experiences);
        ExperienceUnit generated = generatedExperience();
        when(experiences.findById(1L)).thenReturn(Optional.of(generated));
        when(experiences.completeReview(eq(1L), eq(ExperienceUnit.ReviewStatus.VERIFIED), eq(42L), any(), eq(0)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.verify(1L, 42L))
                .isInstanceOf(ExperienceStateConflictException.class);
    }

    private ExperienceUnit generatedExperience() {
        return new ExperienceUnit(1L, 1L, ExperienceUnit.ScenarioType.OBJECTION_HANDLING,
                ExperienceUnit.ObjectionType.PRICE, SalesCase.SalesStage.NEGOTIATION, "buyer", "price",
                "compare cost", "question", "price", 0, 5, "sales", "a".repeat(64),
                ExperienceUnit.ReviewStatus.GENERATED, ExperienceUnit.IndexStatus.NOT_INDEXED, null,
                "local", "experience-extract-v1", null, null, 0, null, null);
    }
}
