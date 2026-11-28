package com.smartchat.smartchat.service;

import com.smartchat.smartchat.config.AppProperties;
import com.smartchat.smartchat.dto.chat.PresenceEvent;
import com.smartchat.smartchat.exception.ResourceNotFoundException;
import com.smartchat.smartchat.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Who is online, shared by all instances through Redis.
 *
 * <h3>Redis keys</h3>
 * <ul>
 *   <li>{@code smartchat:presence:last-seen}: sorted set, member = email, score = last heartbeat (epoch ms).
 *       A user is online while their score is newer than {@code timeout-ms}.</li>
 *   <li>{@code smartchat:presence:sessions}: hash, field = email, value = number of open sessions
 *       across all instances (several tabs/devices).</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>Connect: count + 1 and refresh last-seen. If the user wasn't in the set, broadcast "online".</li>
 *   <li>Disconnect: count - 1. At 0, remove from the set and broadcast "offline".</li>
 *   <li>Heartbeat (every instance): refresh last-seen for the users connected to <i>this</i> instance.</li>
 *   <li>Sweeper (every instance): users whose last-seen is too old are removed and broadcast "offline".
 *       This covers an instance that crashed without sending disconnects. ZREM is atomic, so only the
 *       instance that actually removes the user broadcasts the event.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    static final String LAST_SEEN_KEY = "smartchat:presence:last-seen";
    static final String SESSIONS_KEY = "smartchat:presence:sessions";

    /** Sessions connected to this instance: sessionId -> email. Used for heartbeats. */
    private final Map<String, String> localSessions = new ConcurrentHashMap<>();

    private final StringRedisTemplate redis;
    private final UserService userService;
    private final WebSocketPublisher publisher;
    private final AppProperties appProperties;

    public void sessionOpened(String sessionId, String email) {
        localSessions.put(sessionId, email);
        redis.opsForHash().increment(SESSIONS_KEY, email, 1);

        Boolean newlyOnline = redis.opsForZSet().add(LAST_SEEN_KEY, email, System.currentTimeMillis());
        if (Boolean.TRUE.equals(newlyOnline)) {
            broadcast(email, true);
        }
    }

    public void sessionClosed(String sessionId) {
        String email = localSessions.remove(sessionId);
        if (email == null) {
            return; // already handled: Spring can publish two disconnect events for one session
        }

        Long remaining = redis.opsForHash().increment(SESSIONS_KEY, email, -1);
        if (remaining == null || remaining <= 0) {
            redis.opsForHash().delete(SESSIONS_KEY, email);
            markOffline(email);
        }
    }

    public boolean isOnline(String email) {
        Double lastSeen = redis.opsForZSet().score(LAST_SEEN_KEY, email);
        return lastSeen != null && lastSeen >= onlineThreshold();
    }

    /** Usernames of everyone online right now, sorted alphabetically. */
    public List<String> onlineUsernames() {
        Set<String> emails = redis.opsForZSet().rangeByScore(LAST_SEEN_KEY, onlineThreshold(), Double.MAX_VALUE);
        if (emails == null || emails.isEmpty()) {
            return List.of();
        }
        return userService.getUsernames(emails).stream().sorted().toList();
    }

    @Scheduled(fixedRateString = "${app.presence.heartbeat-interval-ms}")
    public void heartbeat() {
        Set<String> emails = new HashSet<>(localSessions.values());
        if (emails.isEmpty()) {
            return;
        }
        double now = System.currentTimeMillis();
        Set<TypedTuple<String>> tuples = emails.stream()
                .map(email -> (TypedTuple<String>) new DefaultTypedTuple<>(email, now))
                .collect(Collectors.toSet());
        redis.opsForZSet().add(LAST_SEEN_KEY, tuples); // one round trip for all local users
    }

    @Scheduled(fixedRateString = "${app.presence.sweep-interval-ms}")
    public void sweepStaleUsers() {
        Set<String> stale = redis.opsForZSet().rangeByScore(LAST_SEEN_KEY, 0, onlineThreshold() - 1);
        if (stale == null) {
            return;
        }
        for (String email : stale) {
            redis.opsForHash().delete(SESSIONS_KEY, email);
            markOffline(email);
        }
    }

    private void markOffline(String email) {
        Long removed = redis.opsForZSet().remove(LAST_SEEN_KEY, email);
        if (removed != null && removed > 0) {
            broadcast(email, false);
        }
    }

    private void broadcast(String email, boolean online) {
        try {
            publisher.toPresence(new PresenceEvent(userService.getUsername(email), online));
        } catch (ResourceNotFoundException e) {
            log.debug("Presence change for a deleted user {}", email);
        }
    }

    private double onlineThreshold() {
        return System.currentTimeMillis() - appProperties.presence().timeoutMs();
    }
}
