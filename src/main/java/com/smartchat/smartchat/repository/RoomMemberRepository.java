package com.smartchat.smartchat.repository;

import com.smartchat.smartchat.entity.Room;
import com.smartchat.smartchat.entity.RoomMember;
import com.smartchat.smartchat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    boolean existsByRoomAndUser(Room room, User user);
}