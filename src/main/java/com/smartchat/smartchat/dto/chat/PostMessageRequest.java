package com.smartchat.smartchat.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/rooms/{roomId}/messages (the room comes from the URL). */
public record PostMessageRequest(
        @NotBlank(message = "Message content is required")
        @Size(max = MessageLimits.MAX_CONTENT_LENGTH, message = "Message is too long")
        String content,

        Long replyToId
) {
}
