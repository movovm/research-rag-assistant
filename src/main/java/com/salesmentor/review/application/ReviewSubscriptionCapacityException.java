package com.salesmentor.review.application;

public class ReviewSubscriptionCapacityException extends RuntimeException {
    public ReviewSubscriptionCapacityException() {
        super("review event subscription capacity reached");
    }
}
