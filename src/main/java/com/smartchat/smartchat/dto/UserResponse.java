package com.smartchat.smartchat.dto;

public record UserResponse(Long id, String username, String email, String tier, String avatarUrl) {
}
