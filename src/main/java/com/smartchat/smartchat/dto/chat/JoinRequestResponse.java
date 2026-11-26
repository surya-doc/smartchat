package com.smartchat.smartchat.dto.chat;

import java.time.LocalDateTime;

public record JoinRequestResponse(Long id, Long userId, String username, LocalDateTime requestedAt) {
}
