package com.smartchat.smartchat.repository;

import com.smartchat.smartchat.entity.Message;
import com.smartchat.smartchat.entity.MessageReaction;
import com.smartchat.smartchat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {
    List<MessageReaction> findByMessage(Message message);
    Optional<MessageReaction> findByMessageAndUserAndEmoji(Message message, User user, String emoji);
}