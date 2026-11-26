package com.smartchat.smartchat.exception;

/**
 * Thrown when the request conflicts with current state (duplicate email, already a member...). Mapped to HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
