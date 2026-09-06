package com.salesmentor.review;

import com.salesmentor.review.config.ReviewTaskExecutorConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewTaskExecutorConfigurationTest {
    @Test
    void createsIndependentBoundedAbortPolicyExecutor() {
        Executor value = new ReviewTaskExecutorConfiguration().reviewTaskExecutor();
        assertThat(value).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) value;
        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaxPoolSize()).isEqualTo(4);
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(50);
        assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("review-task-");
        executor.shutdown();
    }
}
