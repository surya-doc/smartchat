package com.smartchat.smartchat.controller.chat;

import com.smartchat.smartchat.dto.chat.DirectMessageRequest;
import com.smartchat.smartchat.dto.chat.DirectMessageResponse;
import com.smartchat.smartchat.service.chat.DirectMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dm")
@RequiredArgsConstructor
public class DirectMessageController {

    private final DirectMessageService directMessageService;

    @PostMapping
    public ResponseEntity<DirectMessageResponse> sendMessage(
            @RequestBody DirectMessageRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                directMessageService.sendMessage(request, userDetails.getUsername()));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<DirectMessageResponse>> getConversation(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                directMessageService.getConversation(userId, userDetails.getUsername()));
    }

    @PostMapping("/{userId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        directMessageService.markAsRead(userId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }
}