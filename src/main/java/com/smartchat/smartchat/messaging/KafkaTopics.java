package com.smartchat.smartchat.messaging;

public final class KafkaTopics {

    /** Room message events, keyed by room id so each room's events are processed in order. */
    public static final String ROOM_MESSAGES = "chat.room-messages";

    /** Events that failed every retry end up here (same partition as the original). */
    public static final String ROOM_MESSAGES_DLT = ROOM_MESSAGES + ".DLT";

    private KafkaTopics() {
    }
}
