package com.salesmentor.review.domain;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

public interface ReviewTaskRepository {
    ReviewTask save(ReviewTask task);

    Optional<ReviewTask> findById(Long id);

    Optional<ReviewTask> findByRequestId(String requestId);

    boolean start(Long id, long expectedVersion);

    boolean succeed(Long id, long expectedVersion, String reportJson);

    boolean fail(Long id, long expectedVersion, String failureCode, String failureReason);

    List<ReviewTask> findExpiredRunning(LocalDateTime deadline, int limit);

    boolean timeout(Long id, long expectedVersion, LocalDateTime deadline);
}
