package com.smartchat.smartchat.config;

import com.smartchat.smartchat.websocket.RealtimeRedisSubscriber;
import com.smartchat.smartchat.websocket.WebSocketPublisher;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Redis is used for three things:
 * <ul>
 *   <li>Pub/Sub: every instance subscribes to one channel, so a WebSocket event raised on
 *       any instance reaches clients connected to all instances.</li>
 *   <li>Shared state: presence and rate-limit counters (see PresenceService, RateLimiter).</li>
 *   <li>Cache: {@code @Cacheable} values (spring.cache.type=redis).</li>
 * </ul>
 * Scheduling is enabled here for the presence heartbeat and sweeper.
 */
@Configuration
@EnableCaching
@EnableScheduling
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer realtimeListenerContainer(RedisConnectionFactory connectionFactory,
                                                                   RealtimeRedisSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(WebSocketPublisher.REDIS_CHANNEL));
        return container;
    }
}
