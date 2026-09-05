package com.salesmentor.web;

import com.salesmentor.experience.domain.ExperienceNotFoundException;
import com.salesmentor.experience.domain.ExperienceStateConflictException;
import com.salesmentor.salescase.domain.CaseStateConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ExperienceStateConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> experienceConflict(ExperienceStateConflictException exception) {
        return Map.of("code", "EXPERIENCE_STATE_CONFLICT", "message", safeMessage(exception),
                "requestId", UUID.randomUUID().toString(), "details", Map.of());
    }

    @ExceptionHandler(ExperienceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> experienceNotFound(ExperienceNotFoundException exception) {
        return Map.of("code", "EXPERIENCE_NOT_FOUND", "message", safeMessage(exception),
                "requestId", UUID.randomUUID().toString(), "details", Map.of());
    }

    @ExceptionHandler(CaseStateConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> conflict(CaseStateConflictException exception) {
        return Map.of("code", "CASE_STATE_CONFLICT", "message", safeMessage(exception),
                "requestId", UUID.randomUUID().toString(), "details", Map.of());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, ServerWebInputException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(Exception exception) {
        return Map.of("code", "BAD_REQUEST", "message", safeMessage(exception),
                "requestId", UUID.randomUUID().toString(), "details", Map.of());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> internal(Exception exception) {
        return Map.of("code", "INTERNAL_ERROR", "message", "服务暂时不可用，请查看服务日志",
                "requestId", UUID.randomUUID().toString(), "details", Map.of());
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? "请求参数不合法" : exception.getMessage();
    }
}
