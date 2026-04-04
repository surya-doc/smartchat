package com.smartchat.smartchat.repository;

import com.smartchat.smartchat.entity.DirectMessage;
import com.smartchat.smartchat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    @Query("""
            SELECT dm FROM DirectMessage dm
            WHERE (dm.sender = :userA AND dm.receiver = :userB)
               OR (dm.sender = :userB AND dm.receiver = :userA)
            ORDER BY dm.sentAt ASC
            """)
    List<DirectMessage> findConversation(@Param("userA") User userA, @Param("userB") User userB);
}