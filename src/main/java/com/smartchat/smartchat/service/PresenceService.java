package com.smartchat.smartchat.service;

import com.smartchat.smartchat.dto.chat.PresenceEvent;
import com.smartchat.smartchat.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks who is online from the server side. A user counts as online while they have at least
 * one open WebSocket session (several tabs or devices = several sessions), so closing one tab
 * doesn't mark them offline.
 * <p>
 * The counts live in this JVM's memory, so they only work with a single server instance.
 * Day 2 moves them to Redis so any number of instances share the same view.
 */
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final Map<String, Integer> openSessionsByEmail = new ConcurrentHashMap<>();
    private final UserService userService;
    private final WebSocketPublisher publisher;

    public void sessionOpened(String email) {
        int sessions = openSessionsByEmail.merge(email, 1, Integer::sum);
        if (sessions == 1) {
            publish(email, true);
        }
    }

    public void sessionClosed(String email) {
        // Atomically decrement; returning null removes the entry when the last session closes.
        Integer remaining = openSessionsByEmail.computeIfPresent(email, (key, count) -> count > 1 ? count - 1 : null);
        if (remaining == null) {
            publish(email, false);
        }
    }

    public boolean isOnline(String email) {
        return openSessionsByEmail.containsKey(email);
    }

    private void publish(String email, boolean online) {
        userService.setOnlineStatus(email, online)
                .ifPresent(username -> publisher.toPresence(new PresenceEvent(username, online)));
    }
}
