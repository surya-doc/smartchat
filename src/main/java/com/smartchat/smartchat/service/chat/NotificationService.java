package com.smartchat.smartchat.service.chat;

import com.smartchat.smartchat.dto.chat.NotificationResponse;
import com.smartchat.smartchat.entity.Notification;
import com.smartchat.smartchat.entity.User;
import com.smartchat.smartchat.event.UserQueueEvent;
import com.smartchat.smartchat.exception.ResourceNotFoundException;
import com.smartchat.smartchat.repository.NotificationRepository;
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
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    /** Stores the notification and pushes it to the user's open sessions after the transaction commits. */
    public void notify(User recipient, Notification.NotificationType type, String message, Long referenceId) {
        Notification saved = notificationRepository.save(Notification.builder()
                .user(recipient)
                .type(type)
                .message(message)
                .referenceId(referenceId)
                .build());

        eventPublisher.publishEvent(new UserQueueEvent(
                recipient.getEmail(), WebSocketPublisher.QUEUE_NOTIFICATIONS, toResponse(saved)));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnread(String email) {
        User user = userService.getByEmail(email);
        return notificationRepository.findByUserAndIsReadFalseOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getRecent(String email) {
        User user = userService.getByEmail(email);
        return notificationRepository.findTop100ByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public void markAsRead(Long notificationId, String email) {
        User user = userService.getByEmail(email);
        // Someone else's notification answers 404 too, so ids can't be probed.
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setIsRead(true);
    }

    public void markAllAsRead(String email) {
        notificationRepository.markAllRead(userService.getByEmail(email));
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType().name())
                .message(n.getMessage())
                .referenceId(n.getReferenceId())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
