package com.smartchat.smartchat.messaging;

import java.time.Instant;

/**
 * Payload of events on {@link KafkaTopics#ROOM_MESSAGES}.
 * {@code eventId} is unique per event and lets consumers ignore redeliveries.
 * Consumers skip {@code type}s they don't handle, so new types can be added safely.
 */
public record ChatEvent(
        String eventId,
        String type,
        Long roomId,
        Long messageId,
        Long senderId,
        Instant occurredAt
) {
    public static final String MESSAGE_CREATED = "MESSAGE_CREATED";
}
