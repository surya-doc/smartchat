package com.smartchat.smartchat.exception;

/**
 * Thrown when a requested resource (user, room, message...) does not exist. Mapped to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
