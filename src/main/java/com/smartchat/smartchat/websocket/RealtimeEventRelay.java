package com.smartchat.smartchat.websocket;

import com.smartchat.smartchat.event.RoomMessageEvent;
import com.smartchat.smartchat.event.UserQueueEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes domain events to WebSocket clients <b>after</b> the database transaction commits.
 * <p>
 * If we pushed inside the service method and the transaction then rolled back, clients would
 * see a message that was never saved. {@code AFTER_COMMIT} prevents that.
 * {@code fallbackExecution = true} still delivers events raised outside a transaction.
 */
@Component
@RequiredArgsConstructor
public class RealtimeEventRelay {

    private final WebSocketPublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRoomMessage(RoomMessageEvent event) {
        publisher.toRoom(event.roomId(), event.message());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserQueue(UserQueueEvent event) {
        publisher.toUser(event.recipientEmail(), event.queue(), event.payload());
    }
}
