package com.connecthub.message.exception;

/** The request conflicts with current state (e.g. reacting twice with the same emoji). Maps to 409. */
public class ConflictException extends RuntimeException { public ConflictException(String m) { super(m); } }
