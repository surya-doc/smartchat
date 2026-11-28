package com.smartchat.smartchat.websocket;

import com.smartchat.smartchat.TestcontainersConfiguration;
import com.smartchat.smartchat.dto.RegisterRequest;
import com.smartchat.smartchat.dto.chat.CreateRoomRequest;
import com.smartchat.smartchat.repository.UserRepository;
import com.smartchat.smartchat.service.AuthService;
import com.smartchat.smartchat.service.chat.RoomService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end WebSocket tests: a real STOMP client talks to the app running on a random port.
 * These cover what MockMvc can't: JWT on CONNECT, membership checks on SUBSCRIBE, and that
 * room broadcasts and private /user/queue messages actually reach the right people.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StompIntegrationTests {

    private static final long TIMEOUT_SECONDS = 5;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AuthService authService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redis;

    private final List<StompSession> openSessions = new ArrayList<>();

    @AfterEach
    void disconnectAll() {
        openSessions.forEach(session -> {
            if (session.isConnected()) {
                session.disconnect();
            }
        });
    }

    @Test
    void connectWithoutTokenIsRejected() {
        assertThatThrownBy(() -> connect(null, new ErrorCollector()))
                .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

    @Test
    void roomMessagesAreBroadcastToMembers() throws Exception {
        User owner = register();
        User member = register();
        long roomId = roomService.createRoom(new CreateRoomRequest("Live room", null, false), owner.email()).getId();
        roomService.joinRoom(roomId, member.email());

        StompSession memberSession = connect(member.token(), new ErrorCollector());
        BlockingQueue<Map<String, Object>> received = subscribe(memberSession, "/topic/room." + roomId);

        StompSession ownerSession = connect(owner.token(), new ErrorCollector());
        ownerSession.send("/app/chat.send", Map.of("roomId", roomId, "content", "hello room"));

        Map<String, Object> message = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.get("content")).isEqualTo("hello room");
        assertThat(message.get("senderUsername")).isEqualTo(owner.username());
    }

    @Test
    void directMessagesReachTheReceiversPrivateQueue() throws Exception {
        User alice = register();
        User bob = register();
        long bobId = userRepository.findByEmail(bob.email()).orElseThrow().getId();

        StompSession bobSession = connect(bob.token(), new ErrorCollector());
        BlockingQueue<Map<String, Object>> bobInbox = subscribe(bobSession, "/user/queue/dm");

        StompSession aliceSession = connect(alice.token(), new ErrorCollector());
        aliceSession.send("/app/dm.send", Map.of("receiverId", bobId, "content", "hi bob"));

        Map<String, Object> dm = bobInbox.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(dm).as("Bob should receive the DM in real time").isNotNull();
        assertThat(dm.get("content")).isEqualTo("hi bob");
        assertThat(dm.get("senderUsername")).isEqualTo(alice.username());
    }

    @Test
    void nonMembersCannotSubscribeToARoom() throws Exception {
        User owner = register();
        User outsider = register();
        long roomId = roomService.createRoom(new CreateRoomRequest("Private talk", null, true), owner.email()).getId();

        ErrorCollector errors = new ErrorCollector();
        StompSession outsiderSession = connect(outsider.token(), errors);
        subscribe(outsiderSession, "/topic/room." + roomId);

        assertThat(errors.firstError.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("the server should answer the SUBSCRIBE with an ERROR frame")
                .isNotNull();
    }

    @Test
    void presenceIsBroadcastWhenSomeoneConnects() throws Exception {
        User watcher = register();
        User newcomer = register();

        StompSession watcherSession = connect(watcher.token(), new ErrorCollector());
        BlockingQueue<Map<String, Object>> presence = subscribe(watcherSession, "/topic/presence");

        connect(newcomer.token(), new ErrorCollector());

        // Other tests may also produce presence events, so look for the newcomer's.
        Map<String, Object> event;
        do {
            event = presence.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } while (event != null && !newcomer.username().equals(event.get("username")));

        assertThat(event).as("an online event for the newcomer").isNotNull();
        assertThat(event.get("online")).isEqualTo(true);
    }

    @Test
    void eventsPublishedByAnotherInstanceReachLocalClients() throws Exception {
        User owner = register();
        long roomId = roomService.createRoom(new CreateRoomRequest("Cluster room", null, false), owner.email()).getId();

        StompSession session = connect(owner.token(), new ErrorCollector());
        BlockingQueue<Map<String, Object>> received = subscribe(session, "/topic/room." + roomId);

        // Exactly what WebSocketPublisher on a different instance would put on the channel.
        String envelope = """
                {"destination": "/topic/room.%d", "userEmail": null, "payload": {"content": "sent via instance B"}}
                """.formatted(roomId);
        redis.convertAndSend(WebSocketPublisher.REDIS_CHANNEL, envelope);

        Map<String, Object> message = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.get("content")).isEqualTo("sent via instance B");
    }

    @Test
    void businessErrorsComeBackOnTheErrorQueue() throws Exception {
        User user = register();
        long userId = userRepository.findByEmail(user.email()).orElseThrow().getId();

        StompSession session = connect(user.token(), new ErrorCollector());
        BlockingQueue<Map<String, Object>> errors = subscribe(session, "/user/queue/errors");

        session.send("/app/dm.send", Map.of("receiverId", userId, "content", "talking to myself"));

        Map<String, Object> error = errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.get("message")).isEqualTo("You can't send a message to yourself.");
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private record User(String username, String email, String token) {
    }

    private User register() {
        String username = "ws_" + UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        request.setPassword("secret123");
        return new User(username, request.getEmail(), authService.register(request).getToken());
    }

    private StompSession connect(String token, ErrorCollector errors) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new CompositeMessageConverter(
                List.of(new StringMessageConverter(), new JacksonJsonMessageConverter())));

        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }

        // "/ws/websocket" is the raw WebSocket transport of the SockJS endpoint.
        StompSession session = client
                .connectAsync("ws://localhost:" + port + "/ws/websocket", new WebSocketHttpHeaders(), connectHeaders, errors)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        openSessions.add(session);
        return session;
    }

    @SuppressWarnings("unchecked")
    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) throws InterruptedException {
        BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((Map<String, Object>) payload);
            }
        });
        Thread.sleep(300); // SUBSCRIBE is asynchronous: give the broker a moment to register it
        return queue;
    }

    /** Captures the first ERROR frame or transport failure on a session. */
    private static class ErrorCollector extends StompSessionHandlerAdapter {

        private final CompletableFuture<String> firstError = new CompletableFuture<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            firstError.complete("ERROR frame: " + headers.getFirst("message"));
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                    byte[] payload, Throwable exception) {
            firstError.complete("exception: " + exception.getMessage());
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            firstError.complete("transport error: " + exception.getMessage());
        }
    }
}
