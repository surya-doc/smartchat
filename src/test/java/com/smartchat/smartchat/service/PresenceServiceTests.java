package com.smartchat.smartchat.service;

import com.smartchat.smartchat.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Presence against a real Redis. */
class PresenceServiceTests extends IntegrationTestBase {

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void userStaysOnlineUntilTheLastTabCloses() throws Exception {
        TestUser user = registerUser();
        String tab1 = UUID.randomUUID().toString();
        String tab2 = UUID.randomUUID().toString();

        presenceService.sessionOpened(tab1, user.email());
        presenceService.sessionOpened(tab2, user.email());
        assertThat(presenceService.isOnline(user.email())).isTrue();

        presenceService.sessionClosed(tab1);
        assertThat(presenceService.isOnline(user.email())).isTrue();

        presenceService.sessionClosed(tab2);
        assertThat(presenceService.isOnline(user.email())).isFalse();
    }

    @Test
    void duplicateDisconnectEventsAreIgnored() throws Exception {
        TestUser user = registerUser();
        String tab1 = UUID.randomUUID().toString();
        String tab2 = UUID.randomUUID().toString();
        presenceService.sessionOpened(tab1, user.email());
        presenceService.sessionOpened(tab2, user.email());

        presenceService.sessionClosed(tab1);
        presenceService.sessionClosed(tab1); // Spring can fire SessionDisconnectEvent twice

        assertThat(presenceService.isOnline(user.email())).isTrue();
    }

    @Test
    void usersOfACrashedInstanceAreSweptOffline() throws Exception {
        TestUser user = registerUser();
        // Simulate another instance that registered the user and then died:
        // the entry exists in Redis, but nobody refreshes it any more.
        long twoMinutesAgo = System.currentTimeMillis() - 120_000;
        redis.opsForZSet().add(PresenceService.LAST_SEEN_KEY, user.email(), twoMinutesAgo);
        redis.opsForHash().put(PresenceService.SESSIONS_KEY, user.email(), "1");
        assertThat(presenceService.isOnline(user.email())).isFalse();

        presenceService.sweepStaleUsers();

        assertThat(redis.opsForZSet().score(PresenceService.LAST_SEEN_KEY, user.email())).isNull();
        assertThat(redis.opsForHash().hasKey(PresenceService.SESSIONS_KEY, user.email())).isFalse();
    }

    @Test
    void heartbeatKeepsLocalUsersFresh() throws Exception {
        TestUser user = registerUser();
        presenceService.sessionOpened(UUID.randomUUID().toString(), user.email());
        redis.opsForZSet().add(PresenceService.LAST_SEEN_KEY, user.email(), 0); // pretend it went stale

        presenceService.heartbeat();

        assertThat(presenceService.isOnline(user.email())).isTrue();
    }
}
