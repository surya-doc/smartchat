package com.smartchat.smartchat.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

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
    // new
    private Boolean isEdited;
    private Boolean isDeleted;
    private Long replyToId;                    // ID of the message being replied to
    private String replyToContent;             // preview of the replied message
    private String replyToSenderUsername;      // who sent the replied message
    private Map<String, Long> reactions;       // e.g. {"👍": 3, "❤️": 1}
}