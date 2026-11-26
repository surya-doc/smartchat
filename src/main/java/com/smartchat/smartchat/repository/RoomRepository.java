package com.smartchat.smartchat.repository;

import com.smartchat.smartchat.entity.Room;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {

    @EntityGraph(attributePaths = "createdBy")
    List<Room> findByTypeAndStatusOrderByNameAsc(Room.RoomType type, Room.RoomStatus status);
}
