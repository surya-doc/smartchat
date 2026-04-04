package com.smartchat.smartchat.dto.chat;

import lombok.Data;

@Data
public class DirectMessageRequest {
    private Long receiverId;
    private String content;
}