package com.cfduel.config.ratelimit;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Global per-user API rate limiter (spec §11): at most {@value #MAX_PER_WINDOW}
 * authenticated API requests per user per rolling {@value #WINDOW_SECONDS}s
 * window, keyed {@code ratelimit:api:{userId}} in Redis.
 *
 * <p>Uses the same {@code INCR} + {@code EXPIRE} fixed-window pattern as
 * {@link com.cfduel.chat.ChatRateLimiter} and
 * {@link com.cfduel.code.CodeRunRateLimiter}: the first hit in a window arms the
 * TTL and subsequent hits increment the counter. Redis failures <em>degrade
 * open</em> (allow the request) with a {@code log.warn}, matching the existing
 * limiters and {@code PresenceService}, so a Redis outage never hard-fails the
 * whole API.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ApiRateLimiter {

    private static final String PREFIX = "ratelimit:api:";
    private static final int MAX_PER_WINDOW = 100;
    private static final long WINDOW_SECONDS = 60L;
    private static final Duration WINDOW = Duration.ofSeconds(WINDOW_SECONDS);

    private final StringRedisTemplate redis;

    /** Window length in seconds, surfaced for the {@code Retry-After} header. */
    public long windowSeconds() {
        return WINDOW_SECONDS;
    }

    /** Per-window request cap, surfaced for diagnostics / response bodies. */
    public int limit() {
        return MAX_PER_WINDOW;
    }

    /** @return {@code true} if the request is allowed, {@code false} if rate-limited. */
    public boolean allow(UUID userId) {
        if (userId == null) {
            // No authenticated user — caller decides; never limit anonymous traffic here.
            return true;
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
            log.warn("api rate-limit check failed for {} (failing open): {}", userId, ex.getMessage());
            return true; // degrade open
        }
    }
}
