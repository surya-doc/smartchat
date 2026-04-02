package com.smartchat.smartchat.repository;

import com.smartchat.smartchat.entity.RoomInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoomInvitationRepository extends JpaRepository<RoomInvitation, Long> {
    Optional<RoomInvitation> findByToken(String token);
}