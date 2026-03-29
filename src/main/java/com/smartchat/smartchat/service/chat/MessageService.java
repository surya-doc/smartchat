package com.smartchat.smartchat.service.chat;

import com.smartchat.smartchat.dto.chat.MessageResponse;
import com.smartchat.smartchat.entity.Message;
import com.smartchat.smartchat.entity.Room;
import com.smartchat.smartchat.entity.User;
import com.smartchat.smartchat.repository.MessageRepository;
import com.smartchat.smartchat.repository.RoomRepository;
import com.smartchat.smartchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public MessageResponse saveMessage(Long roomId, String email, String content) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        Message message = Message.builder()
                .content(content)
                .room(room)
                .sender(user)
                .senderType(Message.SenderType.USER)
                .build();

        Message saved = messageRepository.save(message);
        return toResponse(saved);
    }

    public List<MessageResponse> getLast50Messages(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        return messageRepository.findTop50ByRoomOrderBySentAtDesc(room)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private MessageResponse toResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .roomId(message.getRoom().getId())
                .senderUsername(message.getSender().getUsername())
                .senderType(message.getSenderType().name())
                .sentAt(message.getSentAt())
                .build();
    }
}