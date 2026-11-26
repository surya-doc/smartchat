package com.smartchat.smartchat.dto.chat;

/** Sent to /topic/presence when a user's first session opens or last session closes. */
public record PresenceEvent(String username, boolean online) {
}
