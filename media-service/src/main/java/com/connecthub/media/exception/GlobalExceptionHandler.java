package com.connecthub.media.exception;

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

    @ExceptionHandler(MediaPlanLimitException.class)
    public ResponseEntity<Map<String, Object>> handlePlanLimit(MediaPlanLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", ex.getMessage(), "status", 429));
    }

    @ExceptionHandler(MediaStorageQuotaException.class)
    public ResponseEntity<Map<String, Object>> handleQuota(MediaStorageQuotaException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", ex.getMessage(), "status", 413));
    }

    @ExceptionHandler(FileSizeLimitException.class)
    public ResponseEntity<Map<String, Object>> handleFileSize(FileSizeLimitException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", ex.getMessage(), "status", 413));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage(), "status", 400));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        log.error("Unhandled runtime exception: ", ex);
        return com.connecthub.common.web.CommonWebExceptionHandler.internalError();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        log.error("Unhandled exception: ", ex);
        return com.connecthub.common.web.CommonWebExceptionHandler.internalError();
    }
}
