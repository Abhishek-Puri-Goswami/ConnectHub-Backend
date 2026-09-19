package com.connecthub.room.exception;

/** Too many requests; carries how long the caller should wait. Maps to 429 with Retry-After. */
public class TooManyRequestsException extends RuntimeException {
    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
