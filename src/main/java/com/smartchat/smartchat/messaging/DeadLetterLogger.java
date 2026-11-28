package com.smartchat.smartchat.messaging;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Makes dead letters visible in the logs (in production this would feed an alert).
 * The record stays in the DLT topic, so it can be inspected or replayed after a fix.
 */
@Slf4j
@Component
public class DeadLetterLogger {

    @KafkaListener(topics = KafkaTopics.ROOM_MESSAGES_DLT, groupId = "dead-letter-logger")
    public void onDeadLetter(ConsumerRecord<String, String> record,
                             @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String error) {
        log.error("Dead letter from {} (key={}, offset={}): {} | payload={}",
                KafkaTopics.ROOM_MESSAGES, record.key(), record.offset(), error, record.value());
    }
}
