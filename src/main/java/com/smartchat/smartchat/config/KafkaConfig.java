package com.smartchat.smartchat.config;

import com.smartchat.smartchat.messaging.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.core.JacksonException;

@Configuration
public class KafkaConfig {

    private static final int PARTITIONS = 3;

    @Bean
    public NewTopic roomMessagesTopic() {
        return TopicBuilder.name(KafkaTopics.ROOM_MESSAGES).partitions(PARTITIONS).replicas(1).build();
    }

    /** Same partition count, because a dead letter goes to the same partition number as the original. */
    @Bean
    public NewTopic roomMessagesDeadLetterTopic() {
        return TopicBuilder.name(KafkaTopics.ROOM_MESSAGES_DLT).partitions(PARTITIONS).replicas(1).build();
    }

    /**
     * Applied to every {@code @KafkaListener} by Spring Boot.
     * <ul>
     *   <li>Transient failures (Redis or MySQL briefly unavailable): retried 3 times with
     *       exponential backoff (1s, 2s, 4s).</li>
     *   <li>Still failing, or a payload that can never be parsed: published to the ".DLT" topic
     *       with the error in its headers, so the partition isn't blocked and nothing is lost.</li>
     * </ul>
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000);
        backOff.setMultiplier(2.0);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JacksonException.class); // malformed JSON won't fix itself
        return handler;
    }
}
