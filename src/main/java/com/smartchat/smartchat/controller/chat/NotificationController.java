package com.smartchat.smartchat.controller.chat;

import com.smartchat.smartchat.dto.chat.NotificationResponse;
import com.smartchat.smartchat.service.chat.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> getRecent(@AuthenticationPrincipal UserDetails user) {
        return notificationService.getRecent(user.getUsername());
    }

    @GetMapping("/unread")
    public List<NotificationResponse> getUnread(@AuthenticationPrincipal UserDetails user) {
        return notificationService.getUnread(user.getUsername());
    }

    @PutMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        notificationService.markAsRead(id, user.getUsername());
    }

    @PutMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllAsRead(@AuthenticationPrincipal UserDetails user) {
        notificationService.markAllAsRead(user.getUsername());
    }
}
