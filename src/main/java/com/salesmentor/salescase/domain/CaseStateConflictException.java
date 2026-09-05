package com.salesmentor.salescase.domain;

public class CaseStateConflictException extends IllegalStateException {
    public CaseStateConflictException(String message) {
        super(message);
    }
}
