package com.smartchat.smartchat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "join_requests",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "user_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JoinRequestStatus status = JoinRequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;               // which admin approved/rejected

    private LocalDateTime reviewedAt;

    @CreationTimestamp
    private LocalDateTime requestedAt;

    public enum JoinRequestStatus {
        PENDING, APPROVED, REJECTED
    }
}