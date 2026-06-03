package com.cfduel.chat;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis token-bucket rate limiter for chat: at most {@value #MAX_PER_WINDOW}
 * messages per user per rolling 60s window (spec §11).
 *
 * <p>Implemented with the classic {@code INCR} + {@code EXPIRE} fixed-window
 * pattern: the first message in a window creates the key and arms a 60s TTL;
 * subsequent messages increment it. Like {@code PresenceService}, all Redis
 * access is defensive and <em>fails open</em> (allows the message) when Redis
 * is unavailable so chat keeps working in a degraded mode.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatRateLimiter {

    private static final String PREFIX = "chat:rl:";
    private static final int MAX_PER_WINDOW = 60;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;

    /** @return {@code true} if the user is allowed to send right now. */
    public boolean allow(String userId) {
        if (userId == null) {
            return false;
        }
        String key = PREFIX + userId;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // First hit in this window — arm the TTL.
                redis.expire(key, WINDOW);
            }
            return count == null || count <= MAX_PER_WINDOW;
        } catch (RuntimeException ex) {
            log.warn("chat rate-limit check failed for {} (failing open): {}", userId, ex.getMessage());
            return true;
        }
    }
}
