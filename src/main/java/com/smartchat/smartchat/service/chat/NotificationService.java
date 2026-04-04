package com.smartchat.smartchat.service.chat;

import com.smartchat.smartchat.dto.chat.NotificationResponse;
import com.smartchat.smartchat.entity.Notification;
import com.smartchat.smartchat.entity.User;
import com.smartchat.smartchat.repository.NotificationRepository;
import com.smartchat.smartchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // ─── create and push notification in real time ─────────────────────────────

    public void notify(User recipient, Notification.NotificationType type,
                       String message, Long referenceId) {

        Notification notification = Notification.builder()
                .user(recipient)
                .type(type)
                .message(message)
                .referenceId(referenceId)
                .build();

        Notification saved = notificationRepository.save(notification);

        // push to user in real time via WebSocket
        messagingTemplate.convertAndSendToUser(
                recipient.getUsername(),
                "/queue/notifications",
                toResponse(saved)
        );
    }

    // ─── get unread notifications ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnread(String email) {
        User user = getUser(email);
        return notificationRepository
                .findByUserAndIsReadFalseOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ─── get all notifications ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NotificationResponse> getAll(String email) {
        User user = getUser(email);
        return notificationRepository
                .findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ─── mark single notification as read ─────────────────────────────────────

    public void markAsRead(Long notificationId, String email) {
        User user = getUser(email);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("This notification does not belong to you.");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    // ─── mark all as read ──────────────────────────────────────────────────────

    public void markAllAsRead(String email) {
        User user = getUser(email);
        notificationRepository
                .findByUserAndIsReadFalseOrderByCreatedAtDesc(user)
                .forEach(n -> {
                    n.setIsRead(true);
                    notificationRepository.save(n);
                });
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
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