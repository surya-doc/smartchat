package com.smartchat.smartchat.exception;

import lombok.Getter;

/** Thrown when a user exceeds a rate limit. Mapped to HTTP 429 with a Retry-After header. */
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
