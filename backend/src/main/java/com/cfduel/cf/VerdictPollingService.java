package com.cfduel.cf;

import com.cfduel.cf.dto.CfSubmission;
import com.cfduel.duel.DuelParticipant;
import com.cfduel.duel.DuelParticipantRepository;
import com.cfduel.duel.DuelRoom;
import com.cfduel.duel.DuelRoomRepository;
import com.cfduel.duel.DuelService;
import com.cfduel.user.User;
import com.cfduel.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Polls Codeforces for accepted submissions on in-progress duels (spec §10) and
 * ends a duel the moment a participant solves its problem.
 *
 * <p>Runs every 5s. A re-entrancy guard prevents overlapping ticks, and each
 * room is processed in its own try/catch so one failure cannot halt the sweep.
 * Per-call CF rate spacing is enforced inside {@link CfApiClient}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerdictPollingService {

    private final CfApiClient cfApiClient;
    private final DuelService duelService;
    private final DuelRoomRepository duelRoomRepository;
    private final DuelParticipantRepository duelParticipantRepository;
    private final UserRepository userRepository;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 5000)
    public void pollInProgressDuels() {
        if (!running.compareAndSet(false, true)) {
            return; // previous tick still in progress
        }
        try {
            List<DuelRoom> rooms = duelRoomRepository.findByStatus("IN_PROGRESS");
            for (DuelRoom room : rooms) {
                try {
                    pollRoom(room);
                } catch (Exception e) {
                    log.warn("Verdict poll failed for room {}: {}", room.getRoomCode(), e.toString());
                }
            }
        } catch (Exception e) {
            log.warn("Verdict polling sweep failed: {}", e.toString());
        } finally {
            running.set(false);
        }
    }

    private void pollRoom(DuelRoom room) {
        String problemKey = room.getProblemId();
        Instant startedAt = room.getStartedAt();
        if (problemKey == null || startedAt == null) {
            return; // not actually ready to be polled
        }
        long startedAtMillis = startedAt.toEpochMilli();

        List<DuelParticipant> participants = duelParticipantRepository.findByRoomId(room.getId());
        for (DuelParticipant participant : participants) {
            UUID userId = participant.getUserId();
            if (userId == null) {
                continue;
            }
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getCfHandle() == null || user.getCfHandle().isBlank()) {
                continue;
            }

            List<CfSubmission> submissions = cfApiClient.getUserStatus(user.getCfHandle(), 10);
            for (CfSubmission sub : submissions) {
                if (!"OK".equals(sub.verdict())) {
                    continue;
                }
                if (sub.contestId() == null || sub.index() == null || sub.creationTimeSeconds() == null) {
                    continue;
                }
                if (!problemKey.equals(sub.problemKey())) {
                    continue;
                }
                long submissionTimeMillis = sub.creationTimeSeconds() * 1000L;
                if (submissionTimeMillis < startedAtMillis) {
                    continue; // solved before the duel started
                }
                long solveDurationMs = Math.max(0L, submissionTimeMillis - startedAtMillis);
                log.info("Duel {} solved by user {} in {}ms", room.getRoomCode(), userId, solveDurationMs);
                duelService.endDuelWithWinner(room.getRoomCode(), userId, solveDurationMs, "SOLVE");
                return; // duel ended; stop processing this room
            }
        }
    }
}
