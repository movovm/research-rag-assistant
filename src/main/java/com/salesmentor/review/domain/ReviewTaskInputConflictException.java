package com.salesmentor.review.domain;

public class ReviewTaskInputConflictException extends IllegalArgumentException {
    public ReviewTaskInputConflictException() {
        super("requestId already exists with different input");
    }
}
