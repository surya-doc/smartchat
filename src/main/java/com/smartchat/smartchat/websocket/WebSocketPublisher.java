package com.smartchat.smartchat.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The only class that knows the WebSocket destination names.
 * <p>
 * Each instance only has its own clients' sessions in memory, so instead of sending to the
 * local broker directly, every event is published to a Redis channel. All instances (including
 * this one) receive it in {@link RealtimeRedisSubscriber} and deliver it to their local sessions.
 * User destinations are addressed by email because the STOMP principal name is the email.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPublisher {

    public static final String REDIS_CHANNEL = "smartchat:realtime";

    public static final String QUEUE_DM = "/queue/dm";
    public static final String QUEUE_NOTIFICATIONS = "/queue/notifications";
    public static final String QUEUE_ERRORS = "/queue/errors";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public void toRoom(Long roomId, Object payload) {
        publish("/topic/room." + roomId, null, payload);
    }

    public void toRoomTyping(Long roomId, Object payload) {
        publish("/topic/room." + roomId + ".typing", null, payload);
    }

    public void toPresence(Object payload) {
        publish("/topic/presence", null, payload);
    }

    /** Delivered to every open session of that user, on any instance, at /user/queue/... */
    public void toUser(String email, String queue, Object payload) {
        publish(queue, email, payload);
    }

    private void publish(String destination, String userEmail, Object payload) {
        try {
            RealtimeEnvelope envelope = new RealtimeEnvelope(destination, userEmail, jsonMapper.valueToTree(payload));
            redisTemplate.convertAndSend(REDIS_CHANNEL, jsonMapper.writeValueAsString(envelope));
        } catch (RuntimeException e) {
            // Live delivery is best effort: the data is already saved and clients reload
            // history when they reconnect, so a Redis hiccup must not fail the request.
            log.warn("Could not publish realtime event to {}: {}", destination, e.getMessage());
        }
    }
}
