package com.smartchat.smartchat.websocket;

import com.smartchat.smartchat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Presence is driven by the server's own session events, not by the client saying
 * "I'm offline". A closed tab, a crashed browser or a lost network all end the session,
 * so the user is always marked offline correctly.
 */
@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        if (user != null) {
            presenceService.sessionOpened(user.getName());
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user != null) {
            presenceService.sessionClosed(user.getName());
        }
    }
}
