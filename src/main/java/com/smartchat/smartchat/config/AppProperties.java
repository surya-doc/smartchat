package com.smartchat.smartchat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Typed view of the {@code app.*} properties in application.yml.
 * Using one record instead of scattered {@code @Value} fields keeps every
 * setting in one place and fails fast at startup if something is missing.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        String frontendUrl,
        List<String> corsAllowedOrigins,
        Long defaultRoomId,
        Presence presence,
        RateLimit rateLimit
) {

    public record Jwt(String secret, long expirationMs) {
    }

    public record Presence(long heartbeatIntervalMs, long sweepIntervalMs, long timeoutMs) {
    }

    public record RateLimit(int maxMessages, long windowMs) {
    }
}
