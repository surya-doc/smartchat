package com.smartchat.smartchat.messaging;

import com.smartchat.smartchat.config.AppProperties;
import com.smartchat.smartchat.entity.OutboxEvent;
import com.smartchat.smartchat.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sends outbox rows to Kafka and marks them published.
 * <p>
 * Delivery is <b>at least once</b>: if Kafka accepts an event but the "published" update then
 * fails to commit, the event is sent again on the next poll. Consumers are idempotent (they track
 * {@code eventId}), so a duplicate has no effect.
 * <p>
 * If a send fails, the batch stops there instead of skipping ahead, so later events for the same
 * room can't overtake an earlier one. The failed event is retried on the next poll.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AppProperties appProperties;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxEventRepository.lockNextUnpublished(appProperties.outbox().batchSize());

        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.setPublishedAt(LocalDateTime.now());
            } catch (Exception e) {
                event.setAttempts(event.getAttempts() + 1);
                log.warn("Outbox event {} not published (attempt {}): {}",
                        event.getEventId(), event.getAttempts(), e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                break; // keep ordering: don't publish later events before this one
            }
        }
    }

    /** Published events are only kept for debugging; delete them after the retention period. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteOldPublishedEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(appProperties.outbox().retentionDays());
        int deleted = outboxEventRepository.deletePublishedBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} published outbox events older than {}", deleted, cutoff);
        }
    }
}
