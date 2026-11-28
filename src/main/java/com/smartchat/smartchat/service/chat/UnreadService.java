package com.smartchat.smartchat.service.chat;

import com.smartchat.smartchat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unread message counts per user and room, stored in Redis:
 * hash {@code smartchat:unread:{userId}}, field = roomId, value = count.
 * <p>
 * Counts are incremented by the Kafka consumer (asynchronously, so sending a message stays fast)
 * and reset when the user opens the room.
 */
@Service
@RequiredArgsConstructor
public class UnreadService {

    private static final Duration DEDUP_TTL = Duration.ofDays(7);

    /**
     * Idempotent increment in one atomic step: "first time we see this eventId?" and
     * "add 1 for every recipient" either both happen or neither does. Doing them as separate
     * commands would double-count (or lose) an event when the app crashes in between.
     * KEYS[1] = dedup key, KEYS[2..n] = unread hashes. ARGV[1] = dedup TTL (ms), ARGV[2] = room id.
     */
    private static final RedisScript<Long> COUNT_ONCE = new DefaultRedisScript<>("""
            if redis.call('SET', KEYS[1], '1', 'NX', 'PX', ARGV[1]) then
                for i = 2, #KEYS do
                    redis.call('HINCRBY', KEYS[i], ARGV[2], 1)
                end
                return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final UserService userService;
    private final RoomAccessService roomAccessService;

    /** @return false if this event was already counted (a redelivery). */
    public boolean countNewMessage(String eventId, Long roomId, Collection<Long> recipientIds) {
        List<String> keys = new ArrayList<>();
        keys.add("smartchat:processed:unread:" + eventId);
        recipientIds.forEach(id -> keys.add(unreadKey(id)));

        Long applied = redis.execute(COUNT_ONCE, keys, String.valueOf(DEDUP_TTL.toMillis()), String.valueOf(roomId));
        return applied != null && applied == 1L;
    }

    /** roomId -> unread count, only for rooms with something unread. */
    public Map<Long, Long> getUnreadCounts(String email) {
        Long userId = userService.getByEmail(email).getId();
        Map<Object, Object> raw = redis.opsForHash().entries(unreadKey(userId));

        Map<Long, Long> counts = new LinkedHashMap<>();
        raw.forEach((room, count) -> counts.put(Long.valueOf(room.toString()), Long.valueOf(count.toString())));
        return counts;
    }

    public void markRoomRead(Long roomId, String email) {
        roomAccessService.requireActiveMember(roomId, email);
        Long userId = userService.getByEmail(email).getId();
        redis.opsForHash().delete(unreadKey(userId), String.valueOf(roomId));
    }

    private static String unreadKey(Long userId) {
        return "smartchat:unread:" + userId;
    }
}
