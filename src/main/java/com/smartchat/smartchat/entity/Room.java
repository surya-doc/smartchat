package com.smartchat.smartchat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;            // NEW

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomType type = RoomType.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RoomStatus status = RoomStatus.ACTIVE;  // NEW

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum RoomType {
        PUBLIC, PRIVATE, DIRECT            // DIRECT added
    }

    public enum RoomStatus {
        ACTIVE, ARCHIVED                   // NEW
    }
}