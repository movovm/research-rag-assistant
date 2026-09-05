package com.salesmentor.experience.application;

import com.salesmentor.experience.domain.ExperienceNotFoundException;
import com.salesmentor.experience.domain.ExperienceRepository;
import com.salesmentor.experience.domain.ExperienceStateConflictException;
import com.salesmentor.experience.domain.ExperienceUnit;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ExperienceReviewApplicationService {
    private final ExperienceRepository experiences;

    public ExperienceReviewApplicationService(ExperienceRepository experiences) {
        this.experiences = experiences;
    }

    public ExperienceUnit verify(Long id, Long reviewedBy) {
        return review(id, ExperienceUnit.ReviewStatus.VERIFIED, reviewedBy);
    }

    public ExperienceUnit reject(Long id, Long reviewedBy) {
        return review(id, ExperienceUnit.ReviewStatus.REJECTED, reviewedBy);
    }

    private ExperienceUnit review(Long id, ExperienceUnit.ReviewStatus target, Long reviewedBy) {
        if (reviewedBy == null || reviewedBy <= 0) {
            throw new IllegalArgumentException("reviewedBy must be positive");
        }
        ExperienceUnit existing = experiences.findById(id).orElseThrow(() -> new ExperienceNotFoundException(id));
        if (existing.reviewStatus() != ExperienceUnit.ReviewStatus.GENERATED) {
            throw new ExperienceStateConflictException("Experience state has changed");
        }
        if (!experiences.completeReview(id, target, reviewedBy, LocalDateTime.now(), existing.version())) {
            throw new ExperienceStateConflictException("Experience state has changed");
        }
        return experiences.findById(id).orElseThrow(() -> new ExperienceNotFoundException(id));
    }
}
