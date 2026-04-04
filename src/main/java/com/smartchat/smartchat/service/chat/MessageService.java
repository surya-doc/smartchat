package com.smartchat.smartchat.service.chat;

import com.smartchat.smartchat.dto.chat.MessageResponse;
import com.smartchat.smartchat.dto.chat.SendMessageRequest;
import com.smartchat.smartchat.entity.*;
import com.smartchat.smartchat.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class MessageService {

    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final MessageReactionRepository messageReactionRepository;

    // ─── existing: save message (updated to support reply) ────────────────────

    public MessageResponse saveMessage(Long roomId, String email, String content) {
        return saveMessageInternal(roomId, email, content, null);
    }

    public MessageResponse saveMessageWithReply(SendMessageRequest request, String email) {
        return saveMessageInternal(request.getRoomId(), email, request.getContent(), request.getReplyToId());
    }

    private MessageResponse saveMessageInternal(Long roomId, String email, String content, Long replyToId) {
        User user = getUser(email);
        Room room = getRoom(roomId);

        Message.MessageBuilder builder = Message.builder()
                .content(content)
                .room(room)
                .sender(user)
                .senderType(Message.SenderType.USER);

        if (replyToId != null) {
            Message replyTo = messageRepository.findById(replyToId)
                    .orElseThrow(() -> new RuntimeException("Replied message not found"));
            builder.replyTo(replyTo);
        }

        return toResponse(messageRepository.save(builder.build()));
    }

    // ─── existing: get last 50 messages ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MessageResponse> getLast50Messages(Long roomId) {
        Room room = getRoom(roomId);
        return messageRepository.findTop50ByRoomOrderBySentAtDesc(room)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ─── new: edit message ─────────────────────────────────────────────────────

    public MessageResponse editMessage(Long messageId, String newContent, String email) {
        User user = getUser(email);
        Message message = getMessage(messageId);

        if (!message.getSender().getId().equals(user.getId())) {
            throw new RuntimeException("You can only edit your own messages.");
        }
        if (message.getIsDeleted()) {
            throw new RuntimeException("Cannot edit a deleted message.");
        }

        message.setContent(newContent);
        message.setIsEdited(true);
        return toResponse(messageRepository.save(message));
    }

    // ─── new: delete message ───────────────────────────────────────────────────

    public MessageResponse deleteMessage(Long messageId, String email) {
        User user = getUser(email);
        Message message = getMessage(messageId);

        if (!message.getSender().getId().equals(user.getId())) {
            throw new RuntimeException("You can only delete your own messages.");
        }

        message.setIsDeleted(true);
        message.setContent("This message was deleted.");
        return toResponse(messageRepository.save(message));
    }

    // ─── new: toggle reaction ──────────────────────────────────────────────────

    public Map<String, Long> toggleReaction(Long messageId, String emoji, String email) {
        User user = getUser(email);
        Message message = getMessage(messageId);

        // if same user reacts with same emoji → remove it (toggle off)
        messageReactionRepository.findByMessageAndUserAndEmoji(message, user, emoji)
                .ifPresentOrElse(
                        existing -> messageReactionRepository.delete(existing),
                        () -> messageReactionRepository.save(
                                MessageReaction.builder()
                                        .message(message)
                                        .user(user)
                                        .emoji(emoji)
                                        .build()
                        )
                );

        return getReactions(message);
    }

    // ─── new: get reactions ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Long> getReactions(Long messageId) {
        return getReactions(getMessage(messageId));
    }

    private Map<String, Long> getReactions(Message message) {
        return messageReactionRepository.findByMessage(message)
                .stream()
                .collect(Collectors.groupingBy(
                        MessageReaction::getEmoji,
                        Collectors.counting()
                ));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Room getRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
    }

    private Message getMessage(Long messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
    }

    public MessageResponse toResponse(Message message) {
        Map<String, Long> reactions = getReactions(message);

        MessageResponse.MessageResponseBuilder builder = MessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .roomId(message.getRoom().getId())
                .senderUsername(message.getSender().getUsername())
                .senderType(message.getSenderType().name())
                .sentAt(message.getSentAt())
                .isEdited(message.getIsEdited())
                .isDeleted(message.getIsDeleted())
                .reactions(reactions);

        if (message.getReplyTo() != null) {
            builder.replyToId(message.getReplyTo().getId())
                    .replyToContent(message.getReplyTo().getContent())
                    .replyToSenderUsername(message.getReplyTo().getSender().getUsername());
        }

        return builder.build();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> searchMessages(Long roomId, String keyword) {
        Room room = getRoom(roomId);
        return messageRepository.searchMessages(roomId, keyword)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}