package com.salesmentor.review.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ReviewTaskTimeoutSchedulerConfiguration {
    @Bean(name = "reviewTaskTimeoutScheduler", destroyMethod = "shutdown")
    public ScheduledThreadPoolExecutor reviewTaskTimeoutScheduler() {
        return new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "review-timeout-");
            thread.setDaemon(false);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
}
