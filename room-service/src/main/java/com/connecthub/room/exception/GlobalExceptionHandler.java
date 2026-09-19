package com.connecthub.room.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends com.connecthub.common.web.CommonWebExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage(), "status", 404));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> badReq(BadRequestException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage(), "status", 400));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> forbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage(), "status", 403));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, Object>> tooMany(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(Map.of("error", ex.getMessage(), "status", 429));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> unavailable(ServiceUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        log.error("Unhandled runtime exception in room-service: ", ex);
        return com.connecthub.common.web.CommonWebExceptionHandler.internalError();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        log.error("Unhandled exception in room-service: ", ex);
        return com.connecthub.common.web.CommonWebExceptionHandler.internalError();
    }
}
