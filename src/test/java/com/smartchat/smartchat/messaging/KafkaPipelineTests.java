package com.smartchat.smartchat.messaging;

import com.smartchat.smartchat.IntegrationTestBase;
import com.smartchat.smartchat.dto.chat.MessageResponse;
import com.smartchat.smartchat.dto.chat.SendMessageRequest;
import com.smartchat.smartchat.entity.OutboxEvent;
import com.smartchat.smartchat.repository.OutboxEventRepository;
import com.smartchat.smartchat.repository.UserRepository;
import com.smartchat.smartchat.service.chat.MessageService;
import com.smartchat.smartchat.service.chat.UnreadService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole event pipeline against real Kafka, MySQL and Redis:
 * message saved -> outbox row -> published to Kafka -> consumer -> unread counts in Redis.
 * Kafka is asynchronous, so assertions wait (up to a timeout) for the expected state.
 */
class KafkaPipelineTests extends IntegrationTestBase {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private MessageService messageService;

    @Autowired
    private UnreadService unreadService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Test
    void unreadCountsFollowNewMessagesAndResetOnRead() throws Exception {
        TestUser owner = registerUser();
        TestUser alice = registerUser();
        TestUser bob = registerUser();
        long roomId = createRoom(owner, "Unread room", false);
        mockMvc.perform(post("/api/rooms/{id}/join", roomId).header(HttpHeaders.AUTHORIZATION, alice.bearer()));
        mockMvc.perform(post("/api/rooms/{id}/join", roomId).header(HttpHeaders.AUTHORIZATION, bob.bearer()));

        messageService.sendMessage(new SendMessageRequest(roomId, "one", null), owner.email());
        messageService.sendMessage(new SendMessageRequest(roomId, "two", null), owner.email());

        String roomKey = "$['" + roomId + "']";
        await().atMost(TIMEOUT).untilAsserted(() ->
                mockMvc.perform(get("/api/rooms/unread").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                        .andExpect(jsonPath(roomKey).value(2)));

        // the sender has nothing unread in that room
        mockMvc.perform(get("/api/rooms/unread").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(jsonPath(roomKey).doesNotExist());

        // Alice opens the room: her count resets, Bob's doesn't
        mockMvc.perform(post("/api/rooms/{id}/read", roomId).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/rooms/unread").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(jsonPath(roomKey).doesNotExist());
        await().atMost(TIMEOUT).untilAsserted(() ->
                mockMvc.perform(get("/api/rooms/unread").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                        .andExpect(jsonPath(roomKey).value(2)));
    }

    @Test
    void nonMembersCannotMarkARoomRead() throws Exception {
        TestUser owner = registerUser();
        TestUser outsider = registerUser();
        long roomId = createRoom(owner, "Members only read", true);

        mockMvc.perform(post("/api/rooms/{id}/read", roomId).header(HttpHeaders.AUTHORIZATION, outsider.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void savingAMessageWritesAnOutboxEventThatGetsPublished() throws Exception {
        TestUser owner = registerUser();
        long roomId = createRoom(owner, "Outbox room", false);

        MessageResponse message = messageService.sendMessage(new SendMessageRequest(roomId, "hello", null), owner.email());

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Optional<OutboxEvent> event = outboxEventRepository.findAll().stream()
                    .filter(e -> e.getPayload().contains("\"messageId\":" + message.getId() + ","))
                    .findFirst();
            assertThat(event).isPresent();
            assertThat(event.get().getMessageKey()).isEqualTo(String.valueOf(roomId));
            assertThat(event.get().getPublishedAt()).isNotNull();
        });
    }

    @Test
    void aRedeliveredEventIsCountedOnlyOnce() throws Exception {
        TestUser user = registerUser();
        long userId = userRepository.findByEmail(user.email()).orElseThrow().getId();
        long roomId = 900_000 + (System.nanoTime() % 1_000);
        String eventId = UUID.randomUUID().toString();

        boolean first = unreadService.countNewMessage(eventId, roomId, List.of(userId));
        boolean second = unreadService.countNewMessage(eventId, roomId, List.of(userId)); // Kafka redelivery

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(unreadService.getUnreadCounts(user.email())).containsEntry(roomId, 1L);
    }

    @Test
    void aMalformedEventGoesToTheDeadLetterTopic() {
        String garbage = "not-json-" + UUID.randomUUID();
        kafkaTemplate.send(KafkaTopics.ROOM_MESSAGES, "1", garbage);

        try (Consumer<String, String> consumer = consumerFactory.createConsumer("dlt-test-" + UUID.randomUUID(), "test")) {
            consumer.subscribe(List.of(KafkaTopics.ROOM_MESSAGES_DLT));

            ConsumerRecord<String, String> deadLetter = null;
            long deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
            while (deadLetter == null && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (garbage.equals(record.value())) {
                        deadLetter = record;
                    }
                }
            }

            assertThat(deadLetter).as("the malformed record should be in the DLT").isNotNull();
            assertThat(deadLetter.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE))
                    .as("the DLT record carries the error that caused it")
                    .isNotNull();
        }
    }
}
