package com.smartchat.smartchat.service.chat;

import com.smartchat.smartchat.dto.chat.MessageLimits;
import com.smartchat.smartchat.exception.BadRequestException;

/**
 * Validation shared by REST and STOMP. REST bodies are also checked with Bean Validation,
 * but STOMP payloads skip that, so the services enforce the same rules themselves.
 */
final class ContentRules {

    private ContentRules() {
    }

    static String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("Message content is required.");
        }
        if (content.length() > MessageLimits.MAX_CONTENT_LENGTH) {
            throw new BadRequestException("Message is too long (max " + MessageLimits.MAX_CONTENT_LENGTH + " characters).");
        }
        return content;
    }

    static String requireEmoji(String emoji) {
        String value = emoji == null ? "" : emoji.trim();
        if (value.isEmpty() || value.length() > MessageLimits.MAX_EMOJI_LENGTH) {
            throw new BadRequestException("Invalid emoji.");
        }
        return value;
    }
}
