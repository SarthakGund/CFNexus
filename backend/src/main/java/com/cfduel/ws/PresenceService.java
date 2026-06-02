package com.cfduel.ws;

import com.cfduel.auth.OAuth2SuccessHandler;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Tracks player liveness in Redis. A heartbeat writes {@code presence:<userKey> = "online"}
 * with a short TTL so a missed heartbeat naturally expires the key.
 *
 * <p>All Redis access is defensive: if Redis is unavailable the WebSocket layer keeps
 * functioning (presence simply degrades to unknown).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PresenceService {

    private static final String PREFIX = "presence:";
    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;

    /** Mark a user online for {@link #TTL}. {@code userKey} is the user UUID or handle. */
    public void heartbeat(String userKey) {
        if (userKey == null) {
            return;
        }
        try {
            redis.opsForValue().set(PREFIX + userKey, "online", TTL);
        } catch (RuntimeException ex) {
            log.warn("presence heartbeat failed for {}: {}", userKey, ex.getMessage());
        }
    }

    public boolean isOnline(String userKey) {
        if (userKey == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(PREFIX + userKey));
        } catch (RuntimeException ex) {
            log.warn("presence lookup failed for {}: {}", userKey, ex.getMessage());
            return false;
        }
    }

    /**
     * On STOMP disconnect, drop the presence key if we can resolve the user from the
     * session. Forfeit-on-disconnect logic is owned by Phase 6; this only clears presence.
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String userKey = resolveUserKey(event);
        log.info("WS disconnect sessionId={} user={}", event.getSessionId(), userKey);
        if (userKey == null) {
            return;
        }
        try {
            redis.delete(PREFIX + userKey);
        } catch (RuntimeException ex) {
            log.warn("presence cleanup failed for {}: {}", userKey, ex.getMessage());
        }
    }

    private String resolveUserKey(SessionDisconnectEvent event) {
        if (event.getUser() != null) {
            return event.getUser().getName();
        }
        try {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs != null) {
                Object userId = attrs.get(OAuth2SuccessHandler.SESSION_USER_ID);
                return userId != null ? userId.toString() : null;
            }
        } catch (RuntimeException ex) {
            log.debug("could not resolve user from disconnect event: {}", ex.getMessage());
        }
        return null;
    }
}
