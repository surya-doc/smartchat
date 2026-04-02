package com.smartchat.smartchat.repository;

import com.smartchat.smartchat.entity.JoinRequest;
import com.smartchat.smartchat.entity.Room;
import com.smartchat.smartchat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {
    List<JoinRequest> findByRoomAndStatus(Room room, JoinRequest.JoinRequestStatus status);
    Optional<JoinRequest> findByRoomAndUser(Room room, User user);
    boolean existsByRoomAndUserAndStatus(Room room, User user, JoinRequest.JoinRequestStatus status);
}