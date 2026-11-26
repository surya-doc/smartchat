package com.smartchat.smartchat.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DirectMessageRequest(
        @NotNull(message = "Receiver ID is required")
        Long receiverId,

        @NotBlank(message = "Message content is required")
        @Size(max = MessageLimits.MAX_CONTENT_LENGTH, message = "Message is too long")
        String content
) {
}
