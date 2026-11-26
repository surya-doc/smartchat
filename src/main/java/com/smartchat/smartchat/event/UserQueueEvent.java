package com.smartchat.smartchat.event;

/**
 * Something to deliver to one user's private queue (DMs, notifications).
 *
 * @param recipientEmail the user's email, which is their STOMP principal name
 * @param queue          a destination such as {@code /queue/dm}
 */
public record UserQueueEvent(String recipientEmail, String queue, Object payload) {
}
