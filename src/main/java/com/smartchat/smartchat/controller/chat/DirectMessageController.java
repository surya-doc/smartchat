package com.smartchat.smartchat.controller.chat;

import com.smartchat.smartchat.dto.chat.DirectMessageRequest;
import com.smartchat.smartchat.dto.chat.DirectMessageResponse;
import com.smartchat.smartchat.service.chat.DirectMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dm")
@RequiredArgsConstructor
public class DirectMessageController {

    private final DirectMessageService directMessageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DirectMessageResponse sendMessage(@Valid @RequestBody DirectMessageRequest request,
                                             @AuthenticationPrincipal UserDetails user) {
        return directMessageService.sendMessage(request, user.getUsername());
    }

    @GetMapping("/{userId}")
    public List<DirectMessageResponse> getConversation(@PathVariable Long userId,
                                                       @AuthenticationPrincipal UserDetails user) {
        return directMessageService.getConversation(userId, user.getUsername());
    }

    @PostMapping("/{userId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable Long userId, @AuthenticationPrincipal UserDetails user) {
        directMessageService.markAsRead(userId, user.getUsername());
    }
}
