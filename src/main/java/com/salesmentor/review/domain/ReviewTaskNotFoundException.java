package com.salesmentor.review.domain;

public class ReviewTaskNotFoundException extends IllegalArgumentException {
    public ReviewTaskNotFoundException() {
        super("review task not found");
    }
}
