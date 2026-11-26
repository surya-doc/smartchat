package com.smartchat.smartchat.exception;

/**
 * Thrown when the caller is authenticated but not allowed to perform the action. Mapped to HTTP 403.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
