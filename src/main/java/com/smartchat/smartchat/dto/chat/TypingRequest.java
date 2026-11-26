package com.smartchat.smartchat.dto.chat;

import jakarta.validation.constraints.NotNull;

public record TypingRequest(
        @NotNull Long roomId,
        boolean typing
) {
}
