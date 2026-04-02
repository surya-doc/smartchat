package com.smartchat.smartchat.repository;

import com.smartchat.smartchat.entity.Room;
import com.smartchat.smartchat.entity.RoomMember;
import com.smartchat.smartchat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    boolean existsByRoomAndUser(Room room, User user);

    Optional<RoomMember> findByRoomAndUser(Room room, User user);
}