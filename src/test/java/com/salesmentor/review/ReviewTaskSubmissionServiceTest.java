package com.salesmentor.review;

import com.salesmentor.agent.application.SalesReviewAgent;
import com.salesmentor.review.application.ReviewTaskApplicationService;
import com.salesmentor.review.application.ReviewTaskSubmissionService;
import com.salesmentor.review.domain.ReviewTask;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ReviewTaskSubmissionServiceTest {
    @Test
    void pendingTaskIsAcceptedAndRunnableDelegatesToSynchronousService() {
        ReviewTaskApplicationService tasks = mock(ReviewTaskApplicationService.class);
        ReviewTask pending = task(1L, ReviewTask.Status.PENDING);
        when(tasks.find(1L)).thenReturn(pending);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        ReviewTaskSubmissionService service = new ReviewTaskSubmissionService(tasks, queued::set);

        ReviewTaskSubmissionService.ReviewTaskSubmission result = service.submit(1L);

        assertThat(result.status()).isEqualTo(ReviewTaskSubmissionService.ReviewTaskSubmission.Status.ACCEPTED);
        assertThat(result.taskStatus()).isEqualTo(ReviewTask.Status.PENDING);
        queued.get().run();
        verify(tasks).execute(1L);
    }

    @Test
    void rejectionReturnsRejectedAndDoesNotRunOrMutateTask() {
        ReviewTaskApplicationService tasks = mock(ReviewTaskApplicationService.class);
        when(tasks.find(2L)).thenReturn(task(2L, ReviewTask.Status.PENDING));
        Executor rejecting = command -> { throw new RejectedExecutionException("full"); };
        ReviewTaskSubmissionService service = new ReviewTaskSubmissionService(tasks, rejecting);

        assertThat(service.submit(2L).status()).isEqualTo(ReviewTaskSubmissionService.ReviewTaskSubmission.Status.REJECTED);
        verify(tasks, never()).execute(anyLong());
    }

    @Test
    void nonPendingStatesAreNotSubmitted() {
        for (ReviewTask.Status status : new ReviewTask.Status[]{ReviewTask.Status.RUNNING,
                ReviewTask.Status.SUCCEEDED, ReviewTask.Status.FAILED, ReviewTask.Status.PARTIAL_SUCCEEDED}) {
            ReviewTaskApplicationService tasks = mock(ReviewTaskApplicationService.class);
            when(tasks.find(3L)).thenReturn(task(3L, status));
            ReviewTaskSubmissionService service = new ReviewTaskSubmissionService(tasks, command -> {
                throw new AssertionError("executor must not be called");
            });
            assertThat(service.submit(3L).status())
                    .isEqualTo(ReviewTaskSubmissionService.ReviewTaskSubmission.Status.NOT_PENDING);
            verify(tasks, never()).execute(anyLong());
        }
    }

    @Test
    void workerInfrastructureFailureIsContainedAndInvalidIdsRejected() {
        ReviewTaskApplicationService tasks = mock(ReviewTaskApplicationService.class);
        when(tasks.find(4L)).thenReturn(task(4L, ReviewTask.Status.PENDING));
        AtomicReference<Runnable> queued = new AtomicReference<>();
        ReviewTaskSubmissionService service = new ReviewTaskSubmissionService(tasks, queued::set);
        doThrow(new IllegalStateException("database secret")).when(tasks).execute(4L);
        queued.set(() -> {
            try { tasks.execute(4L); } catch (RuntimeException ignored) { }
        });
        service.submit(4L);
        queued.get().run();
        verify(tasks).execute(4L);
        assertThatThrownBy(() -> service.submit(0L)).isInstanceOf(IllegalArgumentException.class);
    }

    private ReviewTask task(Long id, ReviewTask.Status status) {
        return new ReviewTask(id, "request-" + id, null, null, "industry", null, "buyer", "conversation",
                "review goal", status, null, null, null, status == ReviewTask.Status.PENDING ? 0 : 1,
                null, null, null, null, null, null);
    }
}
