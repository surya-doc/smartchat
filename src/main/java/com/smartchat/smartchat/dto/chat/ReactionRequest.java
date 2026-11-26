package com.smartchat.smartchat.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReactionRequest(
        @NotBlank(message = "Emoji is required")
        @Size(max = MessageLimits.MAX_EMOJI_LENGTH, message = "Emoji is too long")
        String emoji
) {
}
