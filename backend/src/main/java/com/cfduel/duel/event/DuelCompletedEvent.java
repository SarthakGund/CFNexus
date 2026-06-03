package com.cfduel.duel.event;

import java.util.List;
import java.util.UUID;

/**
 * Spring {@link org.springframework.context.ApplicationEvent} published by
 * {@code DuelService} once a duel result has been persisted (spec §13). The
 * achievement subsystem listens for it after the transaction commits and
 * evaluates streak / rating / fast-solve / hard-problem / team conditions.
 *
 * <p>This is an in-process application event — distinct from the STOMP
 * {@code DuelEvent} hierarchy broadcast to clients.
 *
 * @param roomId          the completed room's id
 * @param roomType        RATED_1V1 / UNRATED_TEAM / UNRATED_FFA
 * @param winnerId        the winning user, or {@code null} for a draw
 * @param loserIds        all losing users (empty/just the others on a draw)
 * @param resultType      SOLVE / RESIGN / DRAW / DISCONNECT / TIMEOUT
 * @param solveDurationMs solve time in ms when the win came from a SOLVE, else null
 * @param problemId       the room's problem key (e.g. "1234A"), may be null
 * @param problemRating   the problem's rating, may be null
 * @param winnerTeam      the winning team number for team duels, else null
 */
public record DuelCompletedEvent(
        UUID roomId,
        String roomType,
        UUID winnerId,
        List<UUID> loserIds,
        String resultType,
        Long solveDurationMs,
        String problemId,
        Integer problemRating,
        Integer winnerTeam) {
}
