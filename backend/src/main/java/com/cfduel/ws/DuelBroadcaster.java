package com.cfduel.ws;

import com.cfduel.ws.event.DuelEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around {@link SimpMessagingTemplate} for broadcasting duel events
 * and chat to room topics. Agents A and C call into this to push state changes.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DuelBroadcaster {

    private final SimpMessagingTemplate simpMessagingTemplate;

    /** Broadcast a typed duel event to all subscribers of the room. */
    public void broadcast(String roomCode, DuelEvent event) {
        simpMessagingTemplate.convertAndSend("/topic/duel/" + roomCode, event);
    }

    /** Relay a chat payload (already encrypted by the client) to the room's chat topic. */
    public void broadcastChat(String roomCode, Object payload) {
        simpMessagingTemplate.convertAndSend("/topic/duel/" + roomCode + "/chat", payload);
    }

    /**
     * Best-effort direct message to a single user via the user-destination prefix.
     * {@code sessionOrUserId} is the Principal name (the user UUID string) set by
     * the CONNECT interceptor.
     */
    public void toUser(String sessionOrUserId, Object payload) {
        try {
            simpMessagingTemplate.convertAndSendToUser(sessionOrUserId, "/queue/messages", payload);
        } catch (RuntimeException ex) {
            log.warn("toUser delivery failed for {}: {}", sessionOrUserId, ex.getMessage());
        }
    }
}
