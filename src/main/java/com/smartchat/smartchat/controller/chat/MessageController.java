package com.smartchat.smartchat.controller.chat;

import com.smartchat.smartchat.dto.chat.EditMessageRequest;
import com.smartchat.smartchat.dto.chat.MessageResponse;
import com.smartchat.smartchat.dto.chat.ReactionRequest;
import com.smartchat.smartchat.service.chat.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operations on one existing message. New messages are sent over STOMP (/app/chat.send). */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PutMapping("/{messageId}")
    public MessageResponse editMessage(@PathVariable Long messageId,
                                       @Valid @RequestBody EditMessageRequest request,
                                       @AuthenticationPrincipal UserDetails user) {
        return messageService.editMessage(messageId, request.content(), user.getUsername());
    }

    @DeleteMapping("/{messageId}")
    public MessageResponse deleteMessage(@PathVariable Long messageId,
                                         @AuthenticationPrincipal UserDetails user) {
        return messageService.deleteMessage(messageId, user.getUsername());
    }

    @PostMapping("/{messageId}/reactions")
    public MessageResponse toggleReaction(@PathVariable Long messageId,
                                          @Valid @RequestBody ReactionRequest request,
                                          @AuthenticationPrincipal UserDetails user) {
        return messageService.toggleReaction(messageId, request.emoji(), user.getUsername());
    }
}
