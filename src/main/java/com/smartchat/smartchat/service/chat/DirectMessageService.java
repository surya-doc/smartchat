package com.smartchat.smartchat.service.chat;

import com.smartchat.smartchat.dto.chat.DirectMessageRequest;
import com.smartchat.smartchat.dto.chat.DirectMessageResponse;
import com.smartchat.smartchat.entity.DirectMessage;
import com.smartchat.smartchat.entity.Notification;
import com.smartchat.smartchat.entity.User;
import com.smartchat.smartchat.event.UserQueueEvent;
import com.smartchat.smartchat.exception.BadRequestException;
import com.smartchat.smartchat.repository.DirectMessageRepository;
import com.smartchat.smartchat.service.UserService;
import com.smartchat.smartchat.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DirectMessageService {

    private final DirectMessageRepository directMessageRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Saves the DM and pushes it to both users (the sender's other tabs need it too).
     * Works the same whether it was sent over REST or STOMP.
     */
    public DirectMessageResponse sendMessage(DirectMessageRequest request, String senderEmail) {
        if (request.receiverId() == null) {
            throw new BadRequestException("Receiver ID is required.");
        }
        User sender = userService.getByEmail(senderEmail);
        User receiver = userService.getById(request.receiverId());

        if (sender.getId().equals(receiver.getId())) {
            throw new BadRequestException("You can't send a message to yourself.");
        }

        DirectMessage saved = directMessageRepository.save(DirectMessage.builder()
                .sender(sender)
                .receiver(receiver)
                .content(ContentRules.requireContent(request.content()))
                .build());

        DirectMessageResponse response = toResponse(saved);
        eventPublisher.publishEvent(new UserQueueEvent(receiver.getEmail(), WebSocketPublisher.QUEUE_DM, response));
        eventPublisher.publishEvent(new UserQueueEvent(sender.getEmail(), WebSocketPublisher.QUEUE_DM, response));

        notificationService.notify(receiver, Notification.NotificationType.DIRECT_MESSAGE,
                sender.getUsername() + " sent you a message.", sender.getId());
        return response;
    }

    @Transactional(readOnly = true)
    public List<DirectMessageResponse> getConversation(Long otherUserId, String email) {
        User user = userService.getByEmail(email);
        User other = userService.getById(otherUserId);

        return directMessageRepository.findConversation(user, other)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Marks everything the other user sent me as read, in one UPDATE. */
    public void markAsRead(Long otherUserId, String email) {
        User me = userService.getByEmail(email);
        User other = userService.getById(otherUserId);
        directMessageRepository.markConversationRead(other, me);
    }

    private DirectMessageResponse toResponse(DirectMessage dm) {
        return DirectMessageResponse.builder()
                .id(dm.getId())
                .senderId(dm.getSender().getId())
                .senderUsername(dm.getSender().getUsername())
                .receiverId(dm.getReceiver().getId())
                .receiverUsername(dm.getReceiver().getUsername())
                .content(dm.getContent())
                .isRead(dm.getIsRead())
                .sentAt(dm.getSentAt())
                .build();
    }
}
