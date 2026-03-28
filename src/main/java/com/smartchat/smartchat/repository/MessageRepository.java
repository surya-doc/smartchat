package com.smartchat.smartchat.repository;

import com.smartchat.smartchat.entity.Message;
import com.smartchat.smartchat.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findTop50ByRoomOrderBySentAtDesc(Room room);
}