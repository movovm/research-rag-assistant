package com.salesmentor.experience.domain;

import java.util.List;
import java.util.Optional;

public interface ExperienceRepository {
    ExperienceUnit save(ExperienceUnit experience);

    Optional<ExperienceUnit> findById(Long id);

    List<ExperienceUnit> findByCaseId(Long caseId);

    boolean compareAndSetReviewStatus(Long id, ExperienceUnit.ReviewStatus expected,
                                      ExperienceUnit.ReviewStatus target, int version);
}
