package com.smartchat.smartchat.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotNull(message = "Room ID is required")
        Long roomId,

        @NotBlank(message = "Message content is required")
        @Size(max = MessageLimits.MAX_CONTENT_LENGTH, message = "Message is too long")
        String content,

        Long replyToId   // optional: id of the message being replied to
) {
}
