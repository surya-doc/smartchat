package com.smartchat.smartchat.dto.chat;

import java.time.LocalDateTime;

public record InviteLinkResponse(String url, String token, LocalDateTime expiresAt) {
}
