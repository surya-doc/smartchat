package com.smartchat.smartchat.repository;

import com.smartchat.smartchat.entity.DirectMessage;
import com.smartchat.smartchat.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    @EntityGraph(attributePaths = {"sender", "receiver"})
    @Query("""
            SELECT dm FROM DirectMessage dm
            WHERE (dm.sender = :userA AND dm.receiver = :userB)
               OR (dm.sender = :userB AND dm.receiver = :userA)
            ORDER BY dm.sentAt ASC, dm.id ASC
            """)
    List<DirectMessage> findConversation(@Param("userA") User userA, @Param("userB") User userB);

    /** One UPDATE statement instead of loading and saving every message. */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE DirectMessage dm SET dm.isRead = true
            WHERE dm.sender = :sender AND dm.receiver = :receiver AND dm.isRead = false
            """)
    int markConversationRead(@Param("sender") User sender, @Param("receiver") User receiver);
}
