package com.connecthub.websocket.exception;

import com.connecthub.common.web.CommonWebExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** REST error handling for websocket-service's small HTTP surface (unread counts, broadcast, connections). */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends CommonWebExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unhandled exception in websocket-service", ex);
        return internalError();
    }
}
