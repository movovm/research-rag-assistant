package com.salesmentor.experience.domain;

import java.util.List;
import java.util.Collection;
import java.time.LocalDateTime;
import java.util.Optional;

public interface ExperienceRepository {
    ExperienceUnit save(ExperienceUnit experience);

    Optional<ExperienceUnit> findById(Long id);

    List<ExperienceUnit> findByCaseId(Long caseId);

    List<ExperienceUnit> findPublishedIndexedByIds(Collection<Long> ids);

    boolean completeReview(Long id, ExperienceUnit.ReviewStatus target, Long reviewedBy,
                           LocalDateTime reviewedAt, int version);

    boolean claimIndexing(Long id, int version);

    boolean completePublishing(Long id, String vectorRef, int version);

    boolean markIndexFailed(Long id, int version);
}
