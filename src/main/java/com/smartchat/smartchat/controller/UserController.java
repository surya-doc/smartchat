package com.smartchat.smartchat.controller;

import com.smartchat.smartchat.dto.UserResponse;
import com.smartchat.smartchat.service.PresenceService;
import com.smartchat.smartchat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PresenceService presenceService;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserDetails user) {
        return userService.getProfile(user.getUsername());
    }

    /** Snapshot for the UI on load; live changes then arrive on /topic/presence. */
    @GetMapping("/online")
    public List<String> onlineUsers() {
        return presenceService.onlineUsernames();
    }
}
