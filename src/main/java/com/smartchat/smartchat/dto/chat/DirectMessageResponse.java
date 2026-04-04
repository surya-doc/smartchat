package com.smartchat.smartchat.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectMessageResponse {
    private Long id;
    private String senderUsername;
    private String receiverUsername;
    private String content;
    private Boolean isRead;
    private LocalDateTime sentAt;
}