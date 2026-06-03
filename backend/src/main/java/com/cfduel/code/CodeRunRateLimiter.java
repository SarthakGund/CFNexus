package com.cfduel.code;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Per-user token bucket for code runs: {@value #MAX_RUNS} per
 * {@value #WINDOW_SECONDS}s, keyed {@code code:rl:{userId}} in Redis.
 *
 * <p>Uses INCR + EXPIRE: the first increment in a window sets the TTL, so the
 * window slides forward naturally. Redis failures degrade open (allow the run)
 * with a {@code log.warn}, matching {@code PresenceService}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CodeRunRateLimiter {

    private static final int MAX_RUNS = 10;
    private static final long WINDOW_SECONDS = 60L;

    private final StringRedisTemplate redis;

    /** @return {@code true} if the run is allowed, {@code false} if rate-limited. */
    public boolean tryAcquire(UUID userId) {
        String key = "code:rl:" + userId;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(WINDOW_SECONDS));
            }
            return count == null || count <= MAX_RUNS;
        } catch (RuntimeException ex) {
            log.warn("code-run rate limit check failed for {}: {}", userId, ex.getMessage());
            return true; // degrade open
        }
    }
}
