package com.smartchat.smartchat.dto.chat;

import lombok.Data;

@Data
public class TypingRequest {
    private Long roomId;
    private Boolean isTyping;
}