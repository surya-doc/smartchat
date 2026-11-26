package com.smartchat.smartchat.event;

import com.smartchat.smartchat.dto.chat.MessageResponse;

/**
 * A room message was created, edited, deleted or reacted to.
 * Clients upsert by message id, so one event type covers every change.
 */
public record RoomMessageEvent(Long roomId, MessageResponse message) {
}
