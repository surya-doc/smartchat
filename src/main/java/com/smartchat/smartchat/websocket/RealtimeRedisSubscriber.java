package com.smartchat.smartchat.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs on every instance: takes events from the Redis channel and hands them to this
 * instance's in-memory STOMP broker, which delivers them to the sessions connected here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeRedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RealtimeEnvelope envelope = jsonMapper.readValue(message.getBody(), RealtimeEnvelope.class);
            if (envelope.userEmail() == null) {
                messagingTemplate.convertAndSend(envelope.destination(), envelope.payload());
            } else {
                messagingTemplate.convertAndSendToUser(envelope.userEmail(), envelope.destination(), envelope.payload());
            }
        } catch (RuntimeException e) {
            log.warn("Dropping malformed realtime event: {}", e.getMessage());
        }
    }
}
