package com.smartchat.smartchat.controller.chat;

import com.smartchat.smartchat.dto.chat.DirectMessageRequest;
import com.smartchat.smartchat.dto.chat.ErrorMessage;
import com.smartchat.smartchat.dto.chat.SendMessageRequest;
import com.smartchat.smartchat.dto.chat.TypingEvent;
import com.smartchat.smartchat.dto.chat.TypingRequest;
import com.smartchat.smartchat.exception.BadRequestException;
import com.smartchat.smartchat.exception.ConflictException;
import com.smartchat.smartchat.exception.ForbiddenException;
import com.smartchat.smartchat.exception.ResourceNotFoundException;
import com.smartchat.smartchat.exception.TooManyRequestsException;
import com.smartchat.smartchat.service.UserService;
import com.smartchat.smartchat.service.chat.DirectMessageService;
import com.smartchat.smartchat.service.chat.MessageService;
import com.smartchat.smartchat.service.chat.RoomAccessService;
import com.smartchat.smartchat.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP endpoints (client sends to /app/...). The Principal is set by StompAuthChannelInterceptor
 * on CONNECT, so it is never null here; its name is the user's email.
 * Broadcasting happens in the services via after-commit events, not here.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final MessageService messageService;
    private final DirectMessageService directMessageService;
    private final RoomAccessService roomAccessService;
    private final UserService userService;
    private final WebSocketPublisher publisher;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageRequest request, Principal principal) {
        messageService.sendMessage(request, principal.getName());
    }

    @MessageMapping("/dm.send")
    public void sendDirectMessage(@Payload DirectMessageRequest request, Principal principal) {
        directMessageService.sendMessage(request, principal.getName());
    }

    /** Typing indicators are fire-and-forget: nothing is stored. */
    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingRequest request, Principal principal) {
        if (request.roomId() == null) {
            throw new BadRequestException("Room ID is required.");
        }
        roomAccessService.requireActiveMember(request.roomId(), principal.getName());
        String username = userService.getUsername(principal.getName()); // cached in Redis
        publisher.toRoomTyping(request.roomId(), new TypingEvent(request.roomId(), username, request.typing()));
    }

    /** Expected business errors go back only to the session that sent the frame. */
    @MessageExceptionHandler({ResourceNotFoundException.class, ForbiddenException.class,
            BadRequestException.class, ConflictException.class, TooManyRequestsException.class})
    @SendToUser(destinations = WebSocketPublisher.QUEUE_ERRORS, broadcast = false)
    public ErrorMessage handleBusinessError(RuntimeException ex) {
        return new ErrorMessage(ex.getMessage());
    }

    @MessageExceptionHandler
    @SendToUser(destinations = WebSocketPublisher.QUEUE_ERRORS, broadcast = false)
    public ErrorMessage handleUnexpectedError(Exception ex) {
        log.error("Unhandled error in STOMP handler", ex);
        return new ErrorMessage("Something went wrong. Please try again.");
    }
}
