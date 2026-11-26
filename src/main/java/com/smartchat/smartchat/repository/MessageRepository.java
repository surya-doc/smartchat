package com.smartchat.smartchat.repository;

import com.smartchat.smartchat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Latest 50 messages, newest first. The entity graph loads sender and reply in the
     * same SQL query, so mapping 50 messages doesn't trigger 50+ extra queries (N+1).
     */
    @EntityGraph(attributePaths = {"sender", "replyTo", "replyTo.sender"})
    List<Message> findTop50ByRoom_IdOrderBySentAtDescIdDesc(Long roomId);

    /** Case-insensitive search. '!' escapes % and _ typed by the user (see MessageService#escapeLike). */
    @EntityGraph(attributePaths = {"sender", "replyTo", "replyTo.sender"})
    @Query("""
            SELECT m FROM Message m
            WHERE m.room.id = :roomId
              AND m.isDeleted = false
              AND LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
            ORDER BY m.sentAt DESC, m.id DESC
            """)
    List<Message> searchInRoom(@Param("roomId") Long roomId,
                               @Param("keyword") String keyword,
                               Pageable pageable);
}
