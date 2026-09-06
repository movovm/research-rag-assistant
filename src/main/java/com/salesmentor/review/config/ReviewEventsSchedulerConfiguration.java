package com.salesmentor.review.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ReviewEventsSchedulerConfiguration {
    @Bean(name = "reviewEventsScheduler", destroyMethod = "shutdown")
    public ScheduledThreadPoolExecutor reviewEventsScheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2, runnable -> {
            Thread thread = new Thread(runnable, "review-events-");
            thread.setDaemon(false);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
