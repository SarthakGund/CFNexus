package com.cfduel.ws;

import com.cfduel.auth.OAuth2SuccessHandler;
import com.cfduel.duel.DuelParticipant;
import com.cfduel.duel.DuelParticipantRepository;
import com.cfduel.duel.DuelRoom;
import com.cfduel.duel.DuelRoomRepository;
import com.cfduel.ws.event.PlayerDisconnectedEvent;
import com.cfduel.ws.event.PlayerReconnectedEvent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * <p>Also owns disconnection grace-period bookkeeping (spec §16). When a player
 * in an IN_PROGRESS room drops, a {@code disconnect:<roomCode>:<userId>} key is
 * set with a 30s TTL and the pair is added to the {@link #PENDING_KEY} set so
 * {@code DisconnectForfeitService} can scan for expired grace periods (a bare
 * key TTL is not scannable once it expires). A reconnecting heartbeat clears
 * both and re-broadcasts {@code PLAYER_RECONNECTED}.
 *
 * <p>All Redis access is defensive: if Redis is unavailable the WebSocket layer keeps
 * functioning (presence simply degrades to unknown).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PresenceService {

    private static final String PREFIX = "presence:";
    private static final String DISCONNECT_PREFIX = "disconnect:";
    /** Reverse pointer {@code disconnect-room:<userId> -> roomCode} so reconnect needs no DB scan. */
    private static final String DISCONNECT_ROOM_PREFIX = "disconnect-room:";
    /** Redis SET of {@code roomCode:userId} pairs currently inside a grace period. */
    static final String PENDING_KEY = "disconnect:pending";
    private static final Duration TTL = Duration.ofSeconds(30);
    static final int GRACE_PERIOD_SECONDS = 30;

    private final StringRedisTemplate redis;
    private final DuelParticipantRepository participantRepository;
    private final DuelRoomRepository roomRepository;
    private final DuelBroadcaster broadcaster;

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
        // A live heartbeat also counts as a reconnect within the grace period.
        handleReconnect(userKey);
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
     * On STOMP disconnect, drop the presence key. If the user is mid-duel in an
     * IN_PROGRESS room, open a 30s grace period (spec §16) and broadcast
     * {@code PLAYER_DISCONNECTED}; the scheduled forfeit task resolves it later.
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
        startGracePeriod(userKey);
    }

    // ------------------------------------------------------------------
    // Disconnection grace period (spec §16)
    // ------------------------------------------------------------------

    /**
     * If {@code userKey} is a UUID belonging to a participant of an IN_PROGRESS
     * room, set the disconnect key + pending entry and broadcast the event.
     */
    private void startGracePeriod(String userKey) {
        UUID userId = parseUuid(userKey);
        if (userId == null) {
            return;
        }
        DuelRoom room = findActiveRoom(userId);
        if (room == null) {
            return;
        }
        String roomCode = room.getRoomCode();
        try {
            redis.opsForValue().set(disconnectKey(roomCode, userId), "1", TTL);
            redis.opsForValue().set(DISCONNECT_ROOM_PREFIX + userId, roomCode, TTL);
            redis.opsForSet().add(PENDING_KEY, pendingMember(roomCode, userId));
        } catch (RuntimeException ex) {
            log.warn("disconnect bookkeeping failed for {} in {}: {}",
                    userId, roomCode, ex.getMessage());
        }
        broadcaster.broadcast(roomCode, new PlayerDisconnectedEvent(userId, GRACE_PERIOD_SECONDS));
    }

    /**
     * If the user has a live {@code disconnect:*} key, clear it and the pending
     * entry, then broadcast {@code PLAYER_RECONNECTED} so opponents drop the
     * countdown. No-op when the user is not in a grace period.
     */
    private void handleReconnect(String userKey) {
        UUID userId = parseUuid(userKey);
        if (userId == null) {
            return;
        }
        try {
            // Resolve the room purely from Redis (no DB scan on the common path).
            String roomCode = redis.opsForValue().get(DISCONNECT_ROOM_PREFIX + userId);
            if (roomCode == null) {
                return; // not in a grace period
            }
            String key = disconnectKey(roomCode, userId);
            if (Boolean.TRUE.equals(redis.hasKey(key))) {
                redis.delete(key);
                redis.delete(DISCONNECT_ROOM_PREFIX + userId);
                redis.opsForSet().remove(PENDING_KEY, pendingMember(roomCode, userId));
                broadcaster.broadcast(roomCode, new PlayerReconnectedEvent(userId));
            }
        } catch (RuntimeException ex) {
            log.warn("reconnect check failed for {}: {}", userId, ex.getMessage());
        }
    }

    /** First IN_PROGRESS room this user is an active participant of, or {@code null}. */
    DuelRoom findActiveRoom(UUID userId) {
        try {
            List<DuelRoom> inProgress = roomRepository.findByStatus("IN_PROGRESS");
            for (DuelRoom room : inProgress) {
                if (participantRepository.findByRoomIdAndUserId(room.getId(), userId).isPresent()) {
                    return room;
                }
            }
        } catch (RuntimeException ex) {
            log.warn("active-room lookup failed for {}: {}", userId, ex.getMessage());
        }
        return null;
    }

    /** True while the disconnect grace key for this room/user is still alive. */
    boolean hasLiveDisconnectKey(String roomCode, UUID userId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(disconnectKey(roomCode, userId)));
        } catch (RuntimeException ex) {
            log.warn("disconnect key check failed for {} in {}: {}",
                    userId, roomCode, ex.getMessage());
            return true; // fail safe: assume still in grace, don't forfeit
        }
    }

    static String disconnectKey(String roomCode, UUID userId) {
        return DISCONNECT_PREFIX + roomCode + ":" + userId;
    }

    static String pendingMember(String roomCode, UUID userId) {
        return roomCode + ":" + userId;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
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
