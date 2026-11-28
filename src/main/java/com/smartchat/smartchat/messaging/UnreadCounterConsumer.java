package com.smartchat.smartchat.messaging;

import com.smartchat.smartchat.entity.RoomMember;
import com.smartchat.smartchat.repository.RoomMemberRepository;
import com.smartchat.smartchat.service.chat.UnreadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Keeps unread counts up to date. Runs in the "unread-counters" consumer group: with several
 * instances, Kafka splits the partitions between them, so each event is handled by one instance.
 * Events are keyed by room id, so one room's events always land on the same partition, in order.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnreadCounterConsumer {

    private final JsonMapper jsonMapper;
    private final RoomMemberRepository roomMemberRepository;
    private final UnreadService unreadService;

    @KafkaListener(topics = KafkaTopics.ROOM_MESSAGES, groupId = "unread-counters")
    public void onRoomMessageEvent(ConsumerRecord<String, String> record) {
        // A parse error is not retried: the error handler sends the record straight to the DLT.
        ChatEvent event = jsonMapper.readValue(record.value(), ChatEvent.class);
        if (!ChatEvent.MESSAGE_CREATED.equals(event.type())) {
            return;
        }

        List<Long> recipients = roomMemberRepository
                .findUserIdsByRoomIdAndStatus(event.roomId(), RoomMember.MemberStatus.ACTIVE)
                .stream()
                .filter(userId -> !userId.equals(event.senderId()))
                .toList();

        boolean counted = unreadService.countNewMessage(event.eventId(), event.roomId(), recipients);
        if (!counted) {
            log.debug("Event {} already processed, skipping duplicate", event.eventId());
        }
    }
}
