package com.cfduel.friend;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Best-effort sender for friend notifications over the user destination prefix.
 *
 * <p>Separate from {@link com.cfduel.ws.DuelBroadcaster} to avoid file conflicts.
 * The user UUID is the STOMP Principal name set by the CONNECT interceptor in
 * {@link com.cfduel.ws.WebSocketConfig}, so {@code convertAndSendToUser(userId, ...)}
 * reaches a client subscribed to {@code /user/queue/user}.
 *
 * <p>Delivery failures are logged and swallowed: notifications are advisory and
 * must never roll back the REST transaction that triggered them.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FriendNotifier {

    private static final String DESTINATION = "/queue/user";

    private final SimpMessagingTemplate simpMessagingTemplate;

    /** Push a payload to a single user; never throws. */
    public void notifyUser(UUID userId, Object payload) {
        if (userId == null) {
            return;
        }
        try {
            simpMessagingTemplate.convertAndSendToUser(userId.toString(), DESTINATION, payload);
        } catch (RuntimeException ex) {
            log.warn("friend notification delivery failed for {}: {}", userId, ex.getMessage());
        }
    }
}
