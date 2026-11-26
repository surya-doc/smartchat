package com.smartchat.smartchat.websocket;

import com.smartchat.smartchat.security.JwtTokenProvider;
import com.smartchat.smartchat.service.chat.RoomAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Security for the STOMP channel. HTTP security can't protect WebSocket frames,
 * so every inbound frame passes through here first:
 * <ul>
 *   <li>CONNECT: must carry a valid "Authorization: Bearer &lt;jwt&gt;" header, otherwise the
 *       connection is refused. The authenticated user is attached to the session, so later
 *       frames and {@code @MessageMapping} methods receive it as the {@link Principal}.</li>
 *   <li>SUBSCRIBE to {@code /topic/room.{id}} or {@code /topic/room.{id}.typing}: only active
 *       members of that room may listen.</li>
 * </ul>
 * Throwing here makes Spring send a STOMP ERROR frame to the client.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/room\\.(\\d+)(\\.typing)?$");

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final RoomAccessService roomAccessService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            accessor.setUser(authenticate(accessor));
        } else if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private UsernamePasswordAuthenticationToken authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new BadCredentialsException("Missing bearer token");
        }
        String email = jwtTokenProvider.extractEmail(header.substring(BEARER_PREFIX.length()))
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired token"));

        UserDetails user = userDetailsService.loadUserByUsername(email);
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            throw new AccessDeniedException("Not authenticated");
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        Matcher matcher = ROOM_TOPIC.matcher(destination);
        if (matcher.matches()) {
            long roomId = Long.parseLong(matcher.group(1));
            if (!roomAccessService.isActiveMember(roomId, user.getName())) {
                throw new AccessDeniedException("Not a member of room " + roomId);
            }
        }
    }
}
