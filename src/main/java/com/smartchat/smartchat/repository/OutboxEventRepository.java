package com.smartchat.smartchat.repository;

import com.smartchat.smartchat.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Oldest unpublished events, row-locked until the transaction ends.
     * FOR UPDATE (not SKIP LOCKED): if two instances poll at once, the second waits for the
     * first to finish, so events go out in id order and each room's events stay in order.
     * SKIP LOCKED would let instances publish in parallel but could reorder events.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT :limit
            FOR UPDATE
            """, nativeQuery = true)
    List<OutboxEvent> lockNextUnpublished(@Param("limit") int limit);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.publishedAt IS NOT NULL AND e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") LocalDateTime before);
}
