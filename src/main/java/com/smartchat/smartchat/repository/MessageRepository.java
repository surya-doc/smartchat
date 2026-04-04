package com.smartchat.smartchat.repository;

import com.smartchat.smartchat.entity.Message;
import com.smartchat.smartchat.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findTop50ByRoomOrderBySentAtDesc(Room room);

    @Query("SELECT m FROM Message m WHERE m.room.id = :roomId AND LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) AND m.isDeleted = false ORDER BY m.sentAt DESC")
    List<Message> searchMessages(@Param("roomId") Long roomId, @Param("keyword") String keyword);
}