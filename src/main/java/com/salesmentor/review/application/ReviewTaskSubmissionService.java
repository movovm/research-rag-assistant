package com.salesmentor.review.application;

import com.salesmentor.review.domain.ReviewTask;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class ReviewTaskSubmissionService {
    private final ReviewTaskApplicationService tasks;
    private final Executor executor;

    public ReviewTaskSubmissionService(ReviewTaskApplicationService tasks,
                                       @Qualifier("reviewTaskExecutor") Executor executor) {
        if (tasks == null || executor == null) throw new IllegalArgumentException("submission dependencies are required");
        this.tasks = tasks;
        this.executor = executor;
    }

    public ReviewTaskSubmission submit(Long taskId) {
        if (taskId == null || taskId <= 0) throw new IllegalArgumentException("taskId must be positive");
        ReviewTask current = tasks.find(taskId);
        if (current.status() != ReviewTask.Status.PENDING) {
            return new ReviewTaskSubmission(taskId, ReviewTaskSubmission.Status.NOT_PENDING, current.status());
        }
        try {
            executor.execute(() -> {
                try {
                    tasks.execute(taskId);
                } catch (RuntimeException ignored) {
                    // Infrastructure failures must not be converted into a business failure here.
                }
            });
            return new ReviewTaskSubmission(taskId, ReviewTaskSubmission.Status.ACCEPTED, current.status());
        } catch (RejectedExecutionException rejected) {
            return new ReviewTaskSubmission(taskId, ReviewTaskSubmission.Status.REJECTED, current.status());
        }
    }

    public record ReviewTaskSubmission(Long taskId, Status status, ReviewTask.Status taskStatus) {
        public enum Status { ACCEPTED, REJECTED, NOT_PENDING }

        public ReviewTaskSubmission {
            if (taskId == null || taskId <= 0 || status == null || taskStatus == null) {
                throw new IllegalArgumentException("invalid submission result");
            }
        }
    }
}
