package com.smartchat.smartchat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;                     // who receives this notification

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;                // human-readable text e.g. "John wants to join your room"

    @Column
    private Long referenceId;              // ID of the related entity (room, message, etc.)

    @Column(nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum NotificationType {
        JOIN_REQUEST,          // admin gets this when someone requests to join
        JOIN_APPROVED,         // user gets this when request is approved
        JOIN_REJECTED,         // user gets this when request is rejected
        ROOM_INVITE,           // user gets this when invited to private room
        MESSAGE_REACTION,      // user gets this when someone reacts to their message
        DIRECT_MESSAGE         // user gets this when they receive a DM
    }
}