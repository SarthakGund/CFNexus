package com.cfduel.ws;

import com.cfduel.auth.OAuth2SuccessHandler;
import com.cfduel.chat.ChatMessageStore;
import com.cfduel.chat.ChatRateLimiter;
import com.cfduel.duel.DuelParticipantRepository;
import com.cfduel.duel.DuelRoom;
import com.cfduel.duel.DuelRoomRepository;
import com.cfduel.duel.DuelService;
import com.cfduel.ws.event.DrawDeclinedEvent;
import com.cfduel.ws.event.DrawOfferedEvent;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * STOMP {@code @MessageMapping} handlers for in-duel actions. Clients send to
 * {@code /app/duel/{roomCode}/...}; broadcasts go back out via {@link DuelBroadcaster}.
 *
 * <p>The current user UUID is resolved from the {@link StompPrincipal} attached by the
 * CONNECT interceptor, falling back to the STOMP session attributes.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class DuelWebSocketController {

    private final DuelBroadcaster broadcaster;
    private final DuelService duelService;
    private final PresenceService presenceService;
    private final ChatRateLimiter chatRateLimiter;
    private final ChatMessageStore chatMessageStore;
    private final DuelRoomRepository roomRepository;
    private final DuelParticipantRepository participantRepository;

    /** Player signals readiness in the lobby. Phase 2: log only (presence covers liveness). */
    @MessageMapping("/duel/{roomCode}/ready")
    public void ready(@DestinationVariable String roomCode, Principal principal,
            SimpMessageHeaderAccessor headers) {
        UUID userId = currentUserId(principal, headers);
        log.debug("ready room={} user={}", roomCode, userId);
    }

    @MessageMapping("/duel/{roomCode}/resign")
    public void resign(@DestinationVariable String roomCode, Principal principal,
            SimpMessageHeaderAccessor headers) {
        UUID userId = currentUserId(principal, headers);
        if (userId == null) {
            log.warn("resign without resolvable user, room={}", roomCode);
            return;
        }
        duelService.resign(roomCode, userId);
    }

    @MessageMapping("/duel/{roomCode}/offer-draw")
    public void offerDraw(@DestinationVariable String roomCode, Principal principal,
            SimpMessageHeaderAccessor headers) {
        UUID userId = currentUserId(principal, headers);
        if (userId == null) {
            log.warn("offer-draw without resolvable user, room={}", roomCode);
            return;
        }
        broadcaster.broadcast(roomCode, new DrawOfferedEvent(userId));
    }

    @MessageMapping("/duel/{roomCode}/respond-draw")
    public void respondDraw(@DestinationVariable String roomCode,
            @Payload Map<String, Object> payload, Principal principal,
            SimpMessageHeaderAccessor headers) {
        UUID userId = currentUserId(principal, headers);
        boolean accept = Boolean.TRUE.equals(payload.get("accept"));
        if (accept) {
            duelService.endDuelWithWinner(roomCode, null, null, "DRAW");
        } else {
            broadcaster.broadcast(roomCode, new DrawDeclinedEvent(userId));
        }
    }

    /**
     * Relay an end-to-end encrypted chat payload ({@code ciphertext, iv,
     * senderPublicKeyB64}) for a room (spec §11). The server is a pure relay: it
     * validates the sender is a participant, enforces a per-user Redis rate limit,
     * stashes the opaque ciphertext for late joiners, then broadcasts. The payload
     * is never decrypted or logged.
     */
    @MessageMapping("/duel/{roomCode}/chat")
    public void chat(@DestinationVariable String roomCode, @Payload Map<String, Object> payload,
            Principal principal, SimpMessageHeaderAccessor headers) {
        UUID userId = currentUserId(principal, headers);
        if (userId == null) {
            log.warn("chat without resolvable user, room={}", roomCode);
            return;
        }

        // Sender must be a participant of the room.
        Optional<DuelRoom> room = roomRepository.findByRoomCode(roomCode);
        if (room.isEmpty()
                || participantRepository.findByRoomIdAndUserId(room.get().getId(), userId).isEmpty()) {
            log.warn("chat from non-participant user={} room={}", userId, roomCode);
            return;
        }

        // Token-bucket rate limit (60/min/user); fails open if Redis is down.
        if (!chatRateLimiter.allow(userId.toString())) {
            log.debug("chat rate-limited user={} room={}", userId, roomCode);
            return;
        }

        chatMessageStore.store(roomCode, payload);
        broadcaster.broadcastChat(roomCode, payload);
    }

    @MessageMapping("/duel/{roomCode}/heartbeat")
    public void heartbeat(@DestinationVariable String roomCode, Principal principal,
            SimpMessageHeaderAccessor headers) {
        UUID userId = currentUserId(principal, headers);
        if (userId != null) {
            presenceService.heartbeat(userId.toString());
        }
    }

    /**
     * Resolve the acting user's UUID: prefer the {@link StompPrincipal} set on CONNECT,
     * otherwise read the {@code userId} attribute off the shared HTTP/STOMP session.
     */
    private UUID currentUserId(Principal principal, SimpMessageHeaderAccessor headers) {
        String raw = null;
        if (principal != null && principal.getName() != null) {
            raw = principal.getName();
        } else {
            Map<String, Object> attrs = headers.getSessionAttributes();
            if (attrs != null) {
                Object userId = attrs.get(OAuth2SuccessHandler.SESSION_USER_ID);
                if (userId != null) {
                    raw = userId.toString();
                }
            }
        }
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            log.warn("principal name is not a UUID: {}", raw);
            return null;
        }
    }
}
