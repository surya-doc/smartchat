package com.smartchat.smartchat.service.chat;

import com.smartchat.smartchat.dto.chat.DirectMessageRequest;
import com.smartchat.smartchat.dto.chat.DirectMessageResponse;
import com.smartchat.smartchat.entity.DirectMessage;
import com.smartchat.smartchat.entity.Notification;
import com.smartchat.smartchat.entity.User;
import com.smartchat.smartchat.repository.DirectMessageRepository;
import com.smartchat.smartchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DirectMessageService {

    private final DirectMessageRepository directMessageRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public DirectMessageResponse sendMessage(DirectMessageRequest request, String senderEmail) {
        User sender = getUser(senderEmail);
        User receiver = userRepository.findById(request.getReceiverId())
                .orElseThrow(() -> new RuntimeException("Receiver not found"));

        if (sender.getId().equals(receiver.getId())) {
            throw new RuntimeException("You cannot send a message to yourself.");
        }

        DirectMessage dm = DirectMessage.builder()
                .sender(sender)
                .receiver(receiver)
                .content(request.getContent())
                .build();
        // notify receiver
        notificationService.notify(
                receiver,
                Notification.NotificationType.DIRECT_MESSAGE,
                sender.getUsername() + " sent you a message.",
                null
        );
        return toResponse(directMessageRepository.save(dm));
    }

    @Transactional(readOnly = true)
    public List<DirectMessageResponse> getConversation(Long otherUserId, String email) {
        User user = getUser(email);
        User other = userRepository.findById(otherUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return directMessageRepository.findConversation(user, other)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public void markAsRead(Long otherUserId, String email) {
        User user = getUser(email);
        User other = userRepository.findById(otherUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // mark all messages sent by other to user as read
        directMessageRepository.findConversation(user, other)
                .stream()
                .filter(dm -> dm.getReceiver().getId().equals(user.getId()) && !dm.getIsRead())
                .forEach(dm -> {
                    dm.setIsRead(true);
                    directMessageRepository.save(dm);
                });
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private DirectMessageResponse toResponse(DirectMessage dm) {
        return DirectMessageResponse.builder()
                .id(dm.getId())
                .senderUsername(dm.getSender().getUsername())
                .receiverUsername(dm.getReceiver().getUsername())
                .content(dm.getContent())
                .isRead(dm.getIsRead())
                .sentAt(dm.getSentAt())
                .build();
    }
}