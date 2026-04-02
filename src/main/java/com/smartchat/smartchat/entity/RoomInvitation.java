package com.smartchat.smartchat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "room_invitations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String token;                  // UUID token for the invite link

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", nullable = false)
    private User invitedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_user_id")
    private User invitedUser;              // null = open link, set = targeted invite

    @Column(nullable = false)
    private LocalDateTime expiresAt;       // 24 hours from creation

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum InvitationStatus {
        PENDING, ACCEPTED, EXPIRED, REVOKED
    }
}