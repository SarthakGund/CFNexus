package com.cfduel.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Persists already-encrypted chat payloads in Redis so late joiners can backfill
 * recent history (spec §11). Payloads are stored as opaque JSON strings in a
 * per-room list with a 24h TTL; the server never decrypts or inspects them.
 *
 * <p>Defensive throughout (matching {@code PresenceService}): if Redis is down,
 * storing/reading silently degrades and live relay still works.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatMessageStore {

    private static final String PREFIX = "chat:msgs:";
    private static final Duration TTL = Duration.ofHours(24);
    /** Cap retained history per room to bound memory; trims oldest beyond this. */
    private static final long MAX_MESSAGES = 200;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** Append one encrypted payload (already opaque) to the room's history. */
    public void store(String roomCode, Object payload) {
        String key = PREFIX + roomCode;
        try {
            String json = objectMapper.writeValueAsString(payload);
            redis.opsForList().rightPush(key, json);
            redis.opsForList().trim(key, -MAX_MESSAGES, -1);
            redis.expire(key, TTL);
        } catch (Exception ex) {
            log.warn("chat message store failed for room {}: {}", roomCode, ex.getMessage());
        }
    }

    /** Recent encrypted payloads (oldest first) for backfilling a late joiner. */
    public List<String> history(String roomCode) {
        String key = PREFIX + roomCode;
        try {
            List<String> raw = redis.opsForList().range(key, 0, -1);
            return raw == null ? Collections.emptyList() : raw;
        } catch (RuntimeException ex) {
            log.warn("chat history fetch failed for room {}: {}", roomCode, ex.getMessage());
            return Collections.emptyList();
        }
    }
}
