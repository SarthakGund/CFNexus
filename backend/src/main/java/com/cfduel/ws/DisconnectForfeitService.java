package com.cfduel.ws;

import com.cfduel.duel.DuelParticipant;
import com.cfduel.duel.DuelParticipantRepository;
import com.cfduel.duel.DuelRoom;
import com.cfduel.duel.DuelRoomRepository;
import com.cfduel.duel.DuelService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Resolves expired disconnection grace periods (spec §16). Every 5s it scans
 * the {@code disconnect:pending} set written by {@link PresenceService}; for any
 * entry whose 30s key has expired while the user is still offline and the room
 * is still an IN_PROGRESS rated 1v1, the disconnected player forfeits and the
 * opponent is awarded the win (recorded as a {@code DISCONNECT} result).
 *
 * <p>Scheduling is already enabled application-wide (see {@code @EnableScheduling}
 * on {@code CfDuelApplication}, used by {@code VerdictPollingService}).
 *
 * <p>Unrated duels are intentionally left alone here: the player simply remains
 * marked DISCONNECTED (spec §16 — forfeit applies to rated duels only).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DisconnectForfeitService {

    private static final String TYPE_RATED_1V1 = "RATED_1V1";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    private final StringRedisTemplate redis;
    private final PresenceService presenceService;
    private final DuelService duelService;
    private final DuelRoomRepository roomRepository;
    private final DuelParticipantRepository participantRepository;

    @Scheduled(fixedDelay = 5000)
    public void sweepExpiredDisconnects() {
        Set<String> pending;
        try {
            pending = redis.opsForSet().members(PresenceService.PENDING_KEY);
        } catch (RuntimeException ex) {
            log.warn("disconnect sweep failed to read pending set: {}", ex.getMessage());
            return;
        }
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (String member : pending) {
            try {
                process(member);
            } catch (Exception ex) {
                log.warn("disconnect forfeit failed for {}: {}", member, ex.toString());
            }
        }
    }

    /** {@code member} is {@code roomCode:userId}. */
    private void process(String member) {
        int split = member.lastIndexOf(':');
        if (split <= 0 || split == member.length() - 1) {
            removePending(member);
            return;
        }
        String roomCode = member.substring(0, split);
        UUID userId;
        try {
            userId = UUID.fromString(member.substring(split + 1));
        } catch (IllegalArgumentException ex) {
            removePending(member);
            return;
        }

        // Still inside the grace window — leave it for a later tick.
        if (presenceService.hasLiveDisconnectKey(roomCode, userId)) {
            return;
        }

        // Player came back online — no forfeit; just clean up.
        if (presenceService.isOnline(userId.toString())) {
            removePending(member);
            return;
        }

        DuelRoom room = roomRepository.findByRoomCode(roomCode).orElse(null);
        if (room == null || !STATUS_IN_PROGRESS.equals(room.getStatus())) {
            removePending(member); // already over (or gone) — nothing to forfeit
            return;
        }
        if (!TYPE_RATED_1V1.equals(room.getRoomType())) {
            // Unrated: leave the player marked disconnected, do not end the duel.
            removePending(member);
            return;
        }

        List<DuelParticipant> participants = participantRepository.findByRoomId(room.getId());
        Optional<UUID> opponent = participants.stream()
                .map(DuelParticipant::getUserId)
                .filter(uid -> !uid.equals(userId))
                .findFirst();
        if (opponent.isEmpty()) {
            removePending(member);
            return;
        }

        log.info("Forfeiting room {} — user {} disconnected past grace", roomCode, userId);
        duelService.endDuelWithWinner(roomCode, opponent.get(), null, "DISCONNECT");
        removePending(member);
    }

    private void removePending(String member) {
        try {
            redis.opsForSet().remove(PresenceService.PENDING_KEY, member);
        } catch (RuntimeException ex) {
            log.warn("failed to remove pending disconnect {}: {}", member, ex.getMessage());
        }
    }
}
