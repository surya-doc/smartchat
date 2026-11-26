package com.smartchat.smartchat.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * The only class that knows the WebSocket destination names.
 * User destinations are addressed by email because the STOMP principal name is the email.
 */
@Component
@RequiredArgsConstructor
public class WebSocketPublisher {

    public static final String QUEUE_DM = "/queue/dm";
    public static final String QUEUE_NOTIFICATIONS = "/queue/notifications";
    public static final String QUEUE_ERRORS = "/queue/errors";

    private final SimpMessagingTemplate messagingTemplate;

    public void toRoom(Long roomId, Object payload) {
        messagingTemplate.convertAndSend("/topic/room." + roomId, payload);
    }

    public void toRoomTyping(Long roomId, Object payload) {
        messagingTemplate.convertAndSend("/topic/room." + roomId + ".typing", payload);
    }

    public void toPresence(Object payload) {
        messagingTemplate.convertAndSend("/topic/presence", payload);
    }

    /** Delivered to every open session of that user at /user/queue/... */
    public void toUser(String email, String queue, Object payload) {
        messagingTemplate.convertAndSendToUser(email, queue, payload);
    }
}
