package com.connecthub.notification.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Authorization failures must surface as 403/404, not as a generic 500. */
class GlobalExceptionHandlerAuthzTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void forbidden_mapsTo403() {
        var resp = handler.handleForbidden(new ForbiddenException("Not allowed"));
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertEquals(403, resp.getBody().get("status"));
    }

    @Test
    void notFound_mapsTo404() {
        var resp = handler.handleMissing(new ResourceNotFoundException("Notification not found"));
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }
}
