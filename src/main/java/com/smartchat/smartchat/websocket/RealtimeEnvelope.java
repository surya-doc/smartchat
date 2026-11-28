package com.smartchat.smartchat.websocket;

import tools.jackson.databind.JsonNode;

/**
 * What travels over the Redis channel between instances.
 *
 * @param destination STOMP destination, e.g. {@code /topic/room.5} or {@code /queue/dm}
 * @param userEmail   null for a broadcast; otherwise the user whose private queue gets it
 * @param payload     the JSON body, forwarded to clients as-is
 */
public record RealtimeEnvelope(String destination, String userEmail, JsonNode payload) {
}
