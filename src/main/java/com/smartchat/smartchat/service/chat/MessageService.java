package com.smartchat.smartchat.service.chat;

import com.smartchat.smartchat.dto.chat.MessageResponse;
import com.smartchat.smartchat.dto.chat.SendMessageRequest;
import com.smartchat.smartchat.entity.Message;
import com.smartchat.smartchat.entity.MessageReaction;
import com.smartchat.smartchat.entity.Room;
import com.smartchat.smartchat.entity.User;
import com.smartchat.smartchat.event.RoomMessageEvent;
import com.smartchat.smartchat.exception.BadRequestException;
import com.smartchat.smartchat.exception.ForbiddenException;
import com.smartchat.smartchat.exception.ResourceNotFoundException;
import com.smartchat.smartchat.messaging.OutboxService;
import com.smartchat.smartchat.repository.MessageReactionRepository;
import com.smartchat.smartchat.repository.MessageReactionRepository.ReactionCount;
import com.smartchat.smartchat.repository.MessageRepository;
import com.smartchat.smartchat.repository.RoomRepository;
import com.smartchat.smartchat.service.RateLimiter;
import com.smartchat.smartchat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Room messages. Every change (new, edited, deleted, reacted) is broadcast to the room
 * as the full {@link MessageResponse}; clients replace the message with the same id.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MessageService {

    private static final int SEARCH_LIMIT = 50;
    private static final String DELETED_PLACEHOLDER = "This message was deleted.";

    private final MessageRepository messageRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final RoomRepository roomRepository;
    private final UserService userService;
    private final RoomAccessService roomAccessService;
    private final RateLimiter rateLimiter;
    private final OutboxService outboxService;
    private final ApplicationEventPublisher eventPublisher;

    public MessageResponse sendMessage(SendMessageRequest request, String email) {
        if (request.roomId() == null) {
            throw new BadRequestException("Room ID is required.");
        }
        roomAccessService.requireActiveMember(request.roomId(), email);
        rateLimiter.checkMessageRate(email);
        String content = ContentRules.requireContent(request.content());

        User sender = userService.getByEmail(email);
        Room room = roomRepository.getReferenceById(request.roomId()); // membership check proved it exists

        Message.MessageBuilder builder = Message.builder()
                .content(content)
                .room(room)
                .sender(sender)
                .senderType(Message.SenderType.USER);

        if (request.replyToId() != null) {
            Message replyTo = getMessageEntity(request.replyToId());
            // Without this check a member of room A could quote (and so read) a message from room B.
            if (!replyTo.getRoom().getId().equals(request.roomId())) {
                throw new BadRequestException("You can only reply to messages in the same room.");
            }
            builder.replyTo(replyTo);
        }

        Message saved = messageRepository.save(builder.build());
        outboxService.messageCreated(saved); // same transaction: saved together or not at all

        MessageResponse response = toResponse(saved, Map.of()); // a new message has no reactions yet
        eventPublisher.publishEvent(new RoomMessageEvent(request.roomId(), response));
        return response;
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getRecentMessages(Long roomId, String email) {
        roomAccessService.requireActiveMember(roomId, email);
        return toResponses(messageRepository.findTop50ByRoom_IdOrderBySentAtDescIdDesc(roomId));
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> searchMessages(Long roomId, String keyword, String email) {
        roomAccessService.requireActiveMember(roomId, email);

        String term = keyword == null ? "" : keyword.trim();
        if (term.length() < 2) {
            throw new BadRequestException("Search term must be at least 2 characters.");
        }
        return toResponses(messageRepository.searchInRoom(roomId, escapeLike(term), PageRequest.of(0, SEARCH_LIMIT)));
    }

    public MessageResponse editMessage(Long messageId, String newContent, String email) {
        Message message = getMessageEntity(messageId);
        User user = userService.getByEmail(email);

        if (!message.getSender().getId().equals(user.getId())) {
            throw new ForbiddenException("You can only edit your own messages.");
        }
        if (message.getIsDeleted()) {
            throw new BadRequestException("Deleted messages can't be edited.");
        }

        message.setContent(ContentRules.requireContent(newContent));
        message.setIsEdited(true);
        return publishChange(message);
    }

    /** The sender can delete their own message; a room admin can delete any message (moderation). */
    public MessageResponse deleteMessage(Long messageId, String email) {
        Message message = getMessageEntity(messageId);
        User user = userService.getByEmail(email);

        boolean isSender = message.getSender().getId().equals(user.getId());
        if (!isSender && !roomAccessService.isAdmin(message.getRoom().getId(), email)) {
            throw new ForbiddenException("You can only delete your own messages.");
        }
        if (message.getIsDeleted()) {
            return toResponse(message); // deleting twice is a no-op
        }

        message.setIsDeleted(true);
        message.setContent(DELETED_PLACEHOLDER);
        return publishChange(message);
    }

    /** Same user + same emoji again removes the reaction (toggle). */
    public MessageResponse toggleReaction(Long messageId, String emoji, String email) {
        Message message = getMessageEntity(messageId);
        roomAccessService.requireActiveMember(message.getRoom().getId(), email);

        if (message.getIsDeleted()) {
            throw new BadRequestException("You can't react to a deleted message.");
        }
        String value = ContentRules.requireEmoji(emoji);
        User user = userService.getByEmail(email);

        messageReactionRepository.findByMessageAndUserAndEmoji(message, user, value)
                .ifPresentOrElse(
                        messageReactionRepository::delete,
                        () -> messageReactionRepository.save(MessageReaction.builder()
                                .message(message)
                                .user(user)
                                .emoji(value)
                                .build()));
        messageReactionRepository.flush(); // make the change visible to the count query below

        return publishChange(message);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private MessageResponse publishChange(Message message) {
        MessageResponse response = toResponse(message);
        eventPublisher.publishEvent(new RoomMessageEvent(message.getRoom().getId(), response));
        return response;
    }

    private Message getMessageEntity(Long messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
    }

    /** Maps a page of messages with one extra query for all their reactions (not one per message). */
    private List<MessageResponse> toResponses(List<Message> messages) {
        Map<Long, Map<String, Long>> reactions = reactionsFor(messages.stream().map(Message::getId).toList());
        return messages.stream()
                .map(message -> toResponse(message, reactions.getOrDefault(message.getId(), Map.of())))
                .toList();
    }

    private MessageResponse toResponse(Message message) {
        Map<String, Long> reactions = reactionsFor(List.of(message.getId())).getOrDefault(message.getId(), Map.of());
        return toResponse(message, reactions);
    }

    private Map<Long, Map<String, Long>> reactionsFor(List<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<String, Long>> result = new HashMap<>();
        for (ReactionCount count : messageReactionRepository.countByMessageIds(messageIds)) {
            result.computeIfAbsent(count.getMessageId(), id -> new LinkedHashMap<>())
                    .put(count.getEmoji(), count.getTotal());
        }
        return result;
    }

    private MessageResponse toResponse(Message message, Map<String, Long> reactions) {
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

        Message replyTo = message.getReplyTo();
        if (replyTo != null) {
            builder.replyToId(replyTo.getId())
                    .replyToContent(replyTo.getContent())
                    .replyToSenderUsername(replyTo.getSender().getUsername());
        }
        return builder.build();
    }

    /** Escapes LIKE wildcards so searching for "50%" matches the text "50%", not everything. */
    static String escapeLike(String term) {
        return term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
