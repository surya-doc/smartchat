package com.smartchat.smartchat.repository;

import com.smartchat.smartchat.entity.Message;
import com.smartchat.smartchat.entity.MessageReaction;
import com.smartchat.smartchat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    Optional<MessageReaction> findByMessageAndUserAndEmoji(Message message, User user, String emoji);

    /** Reaction counts for many messages in one GROUP BY query. */
    @Query("""
            SELECT r.message.id AS messageId, r.emoji AS emoji, COUNT(r) AS total
            FROM MessageReaction r
            WHERE r.message.id IN :messageIds
            GROUP BY r.message.id, r.emoji
            """)
    List<ReactionCount> countByMessageIds(@Param("messageIds") Collection<Long> messageIds);

    interface ReactionCount {
        Long getMessageId();

        String getEmoji();

        Long getTotal();
    }
}
