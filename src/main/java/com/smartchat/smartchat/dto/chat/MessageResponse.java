package com.smartchat.smartchat.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageResponse {

    private Long id;
    private String content;
    private Long roomId;
    private String senderUsername;
    private String senderType;
    private LocalDateTime sentAt;
}