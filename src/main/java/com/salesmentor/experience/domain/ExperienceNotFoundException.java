package com.salesmentor.experience.domain;

public class ExperienceNotFoundException extends RuntimeException {
    public ExperienceNotFoundException(Long id) {
        super("Experience not found: " + id);
    }
}
