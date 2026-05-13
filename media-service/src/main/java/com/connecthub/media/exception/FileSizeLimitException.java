package com.connecthub.media.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when an uploaded file exceeds the 2MB per-file size limit. Maps to 413 Payload Too Large. */
@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
public class FileSizeLimitException extends RuntimeException {
    public FileSizeLimitException(String message) { super(message); }
}
