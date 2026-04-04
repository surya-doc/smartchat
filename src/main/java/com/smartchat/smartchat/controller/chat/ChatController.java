package com.smartchat.smartchat.controller.chat;

import com.smartchat.smartchat.dto.chat.*;
import com.smartchat.smartchat.service.UserService;
import com.smartchat.smartchat.service.chat.DirectMessageService;
import com.smartchat.smartchat.service.chat.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;
    private final DirectMessageService directMessageService;
    private final UserService userService;

    // ─── existing: send room message (now supports reply) ─────────────────────

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageRequest request, Principal principal) {
        MessageResponse response = messageService.saveMessageWithReply(request, principal.getName());
        messagingTemplate.convertAndSend("/topic/room." + request.getRoomId(), response);
    }

    // ─── existing: send DM ─────────────────────────────────────────────────────

    @MessageMapping("/dm.send")
    public void sendDirectMessage(@Payload DirectMessageRequest request, Principal principal) {
        DirectMessageResponse response = directMessageService.sendMessage(request, principal.getName());

        messagingTemplate.convertAndSendToUser(
                response.getReceiverUsername(), "/queue/dm", response);

        messagingTemplate.convertAndSendToUser(
                principal.getName(), "/queue/dm", response);
    }

    // ─── online presence ──────────────────────────────────────────────────────

    @MessageMapping("/user.online")
    public void userOnline(Principal principal) {
        userService.setOnlineStatus(principal.getName(), true);
        messagingTemplate.convertAndSend("/topic/presence",
                Optional.of(Map.of("username", principal.getName(), "isOnline", true)));
    }

    @MessageMapping("/user.offline")
    public void userOffline(Principal principal) {
        userService.setOnlineStatus(principal.getName(), false);
        messagingTemplate.convertAndSend("/topic/presence",
                Optional.of(Map.of("username", principal.getName(), "isOnline", false)));
    }

// ─── typing indicator (no DB — pure WebSocket) ────────────────────────────

    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingRequest request, Principal principal) {
        messagingTemplate.convertAndSend(
                "/topic/room." + request.getRoomId() + ".typing",
                Optional.of(Map.of("username", principal.getName(), "isTyping", request.getIsTyping()))
        );
    }
}