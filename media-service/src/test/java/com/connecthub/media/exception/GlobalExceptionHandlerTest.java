package com.connecthub.media.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlePlanLimit_returns429() {
        var ex = new MediaPlanLimitException("Rate limit exceeded");
        var resp = handler.handlePlanLimit(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resp.getBody()).containsEntry("error", "Rate limit exceeded");
        assertThat(resp.getBody()).containsEntry("status", 429);
    }

    @Test
    void handleQuota_returns413() {
        var ex = new MediaStorageQuotaException("Storage full");
        var resp = handler.handleQuota(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(resp.getBody()).containsEntry("error", "Storage full");
        assertThat(resp.getBody()).containsEntry("status", 413);
    }

    @Test
    void handleFileSize_returns413() {
        // Fix #3: FileSizeLimitException is now a separate typed exception (not RuntimeException)
        var ex = new FileSizeLimitException("File exceeds 2 MB limit");
        var resp = handler.handleFileSize(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(resp.getBody()).containsEntry("error", "File exceeds 2 MB limit");
        assertThat(resp.getBody()).containsEntry("status", 413);
    }

    @Test
    void handleBadRequest_returns400() {
        // Fix #3: BadRequestException is now a typed exception for empty file / disallowed MIME
        var ex = new BadRequestException("File type not allowed");
        var resp = handler.handleBadRequest(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).containsEntry("error", "File type not allowed");
        assertThat(resp.getBody()).containsEntry("status", 400);
    }

    @Test
    void handleRuntime_nowReturns500_notBadRequest() {
        // Fix #3: The fallback RuntimeException handler now returns 500 (not 400).
        // Previously all RuntimeExceptions returned 400, masking real server errors.
        var ex = new RuntimeException("Unexpected internal failure");
        var resp = handler.handleRuntime(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody()).containsEntry("error", "Unexpected internal failure");
    }
}
