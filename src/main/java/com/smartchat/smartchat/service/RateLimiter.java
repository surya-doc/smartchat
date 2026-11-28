package com.smartchat.smartchat.service;

import com.smartchat.smartchat.config.AppProperties;
import com.smartchat.smartchat.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fixed-window rate limiter backed by Redis, so the limit is shared by all instances.
 * <p>
 * Each user gets one counter per window (e.g. per 10 seconds). The Lua script makes
 * "increment and, if new, set the expiry" a single atomic step. Doing INCR and EXPIRE as two
 * separate calls could leave a counter without an expiry if the app died in between, and that
 * user would be blocked forever.
 * <p>
 * Trade-off: a fixed window allows a short burst of up to 2x the limit across a window boundary.
 * A sliding-window or token-bucket limiter avoids that at the cost of more Redis work.
 */
@Service
@RequiredArgsConstructor
public class RateLimiter {

    private static final RedisScript<Long> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AppProperties appProperties;

    /** Room messages and DMs share one budget per user. */
    public void checkMessageRate(String email) {
        AppProperties.RateLimit limit = appProperties.rateLimit();
        long now = System.currentTimeMillis();
        long window = now / limit.windowMs();
        String key = "smartchat:ratelimit:messages:" + email + ":" + window;

        Long count = redis.execute(INCREMENT_WITH_EXPIRY, List.of(key), String.valueOf(limit.windowMs()));
        if (count != null && count > limit.maxMessages()) {
            long msLeftInWindow = (window + 1) * limit.windowMs() - now;
            throw new TooManyRequestsException(
                    "You're sending messages too fast. Please wait a moment.",
                    Math.max(1, (msLeftInWindow + 999) / 1000));
        }
    }
}
