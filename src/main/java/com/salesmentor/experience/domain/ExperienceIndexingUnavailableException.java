package com.salesmentor.experience.domain;

public class ExperienceIndexingUnavailableException extends RuntimeException {
    public ExperienceIndexingUnavailableException() {
        super("Experience indexing is temporarily unavailable");
    }
}
