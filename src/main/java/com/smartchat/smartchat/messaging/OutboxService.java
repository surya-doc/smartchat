package com.smartchat.smartchat.messaging;

import com.smartchat.smartchat.entity.Message;
import com.smartchat.smartchat.entity.OutboxEvent;
import com.smartchat.smartchat.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Records domain events in the outbox table. MANDATORY propagation: this must run inside the
 * caller's transaction, otherwise the event and the change it describes could disagree.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    public void messageCreated(Message message) {
        ChatEvent event = new ChatEvent(
                UUID.randomUUID().toString(),
                ChatEvent.MESSAGE_CREATED,
                message.getRoom().getId(),
                message.getId(),
                message.getSender().getId(),
                Instant.now());

        outboxEventRepository.save(OutboxEvent.builder()
                .eventId(event.eventId())
                .eventType(event.type())
                .topic(KafkaTopics.ROOM_MESSAGES)
                .messageKey(String.valueOf(event.roomId()))
                .payload(jsonMapper.writeValueAsString(event))
                .build());
    }
}
