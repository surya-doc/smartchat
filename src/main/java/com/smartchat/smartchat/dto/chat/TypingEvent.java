package com.smartchat.smartchat.dto.chat;

/** Sent to /topic/room.{id}.typing. Never stored. */
public record TypingEvent(Long roomId, String username, boolean typing) {
}
