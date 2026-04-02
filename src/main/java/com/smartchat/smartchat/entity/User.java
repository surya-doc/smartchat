package com.smartchat.smartchat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserTier tier = UserTier.FREE;

    @Column(length = 500)
    private String avatarUrl;              // NEW

    @Column(nullable = false)
    @Builder.Default
    private Boolean isOnline = false;      // NEW

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum UserTier {
        FREE, PREMIUM
    }
}