package com.smartchat.smartchat.controller.chat;


import com.smartchat.smartchat.dto.chat.MessageResponse;
import com.smartchat.smartchat.dto.chat.SendMessageRequest;
import com.smartchat.smartchat.service.chat.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageRequest request,
                            Principal principal) {

        // save to database
        MessageResponse response = messageService.saveMessage(
                request.getRoomId(),
                principal.getName(),
                request.getContent()
        );

        // broadcast to everyone in the room
        messagingTemplate.convertAndSend(
                "/topic/room." + request.getRoomId(),
                response
        );
    }
}