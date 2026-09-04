package com.salesmentor.review.domain;

import java.util.Optional;

public interface ReviewTaskRepository {
    ReviewTask save(ReviewTask task);

    Optional<ReviewTask> findById(Long id);

    Optional<ReviewTask> findByRequestId(String requestId);

    boolean compareAndSetStatus(Long id, ReviewTask.Status expected, ReviewTask.Status target);
}
