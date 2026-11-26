package com.smartchat.smartchat.dto.chat;

/** Sent to /user/queue/errors when a STOMP message is rejected. */
public record ErrorMessage(String message) {
}
