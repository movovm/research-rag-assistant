package io.github.portfolio.rag.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, ServerWebInputException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(Exception exception) {
        return Map.of("error", "bad_request", "message", safeMessage(exception));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> internal(Exception exception) {
        return Map.of("error", "internal_error", "message", "服务暂时不可用，请查看服务日志");
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? "请求参数不合法" : exception.getMessage();
    }
}
