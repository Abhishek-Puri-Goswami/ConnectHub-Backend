package com.connecthub.room.exception;

/** A service this request depends on cannot be reached right now. Maps to 503. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) { super(message); }
}
