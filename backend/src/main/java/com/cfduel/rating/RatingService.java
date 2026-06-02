package com.cfduel.rating;

import com.cfduel.duel.DuelRoom;
import com.cfduel.user.User;
import com.cfduel.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ELO rating engine for rated 1v1 duels (spec §9).
 *
 * <p>Uses a dynamic K-factor (64 for the first 10 games, 32 up to 30, then 16),
 * scaled by a problem-difficulty modifier so that solving a problem rated far
 * above the players' level is worth slightly more. Ratings are floored at 100.
 *
 * <p>Each call mutates and persists both {@link User} rows, appends two
 * {@code rating_history} rows and two {@code match_history} rows. All work runs
 * inside the caller's transaction (see {@code Transactional} propagation).
 */
@Service
@RequiredArgsConstructor
public class RatingService {

    private static final int RATING_FLOOR = 100;

    private final UserRepository userRepository;
    private final RatingHistoryRepository ratingHistoryRepository;
    private final MatchHistoryRepository matchHistoryRepository;

    /**
     * Result of a decisive rated duel, used to populate the {@code duel_results}
     * rating columns.
     */
    public record Decisive(
            int winnerBefore, int winnerAfter, int loserBefore, int loserAfter, int delta) {
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Applies a decisive (win/loss) rated result: updates both ratings and
     * W/L counters, records history, and returns the before/after snapshot.
     *
     * @param resultType the duel-level result type (e.g. SOLVE, RESIGN, DISCONNECT)
     *                   — used only to label the loser's match-history row.
     */
    @Transactional
    public Decisive applyDecisive(
            DuelRoom room, User winner, User loser, String resultType, Long solveDurationMs) {
        int winnerBefore = ratingOf(winner);
        int loserBefore = ratingOf(loser);

        int winnerGames = totalRatedGames(winner);
        int loserGames = totalRatedGames(loser);

        int winnerAfter = newRating(winnerBefore, loserBefore, 1.0, winnerGames, room.getProblemRating());
        int loserAfter = newRating(loserBefore, winnerBefore, 0.0, loserGames, room.getProblemRating());

        winner.setDuelRating(winnerAfter);
        winner.setDuelWins(safe(winner.getDuelWins()) + 1);
        loser.setDuelRating(loserAfter);
        loser.setDuelLosses(safe(loser.getDuelLosses()) + 1);
        userRepository.save(winner);
        userRepository.save(loser);

        recordHistory(room, winner, winnerAfter, winnerAfter - winnerBefore);
        recordHistory(room, loser, loserAfter, loserAfter - loserBefore);

        String loserResult = "RESIGN".equals(resultType) ? "RESIGN" : "LOSS";
        recordMatch(room, winner, loser, "WIN", winnerBefore, winnerAfter, solveDurationMs);
        recordMatch(room, loser, winner, loserResult, loserBefore, loserAfter, solveDurationMs);

        return new Decisive(winnerBefore, winnerAfter, loserBefore, loserAfter, winnerAfter - winnerBefore);
    }

    /**
     * Applies a drawn rated result: both players move toward the expectation,
     * draw counters increment, and history is recorded for both.
     */
    @Transactional
    public void applyDraw(DuelRoom room, User a, User b) {
        int aBefore = ratingOf(a);
        int bBefore = ratingOf(b);

        int aAfter = newRating(aBefore, bBefore, 0.5, totalRatedGames(a), room.getProblemRating());
        int bAfter = newRating(bBefore, aBefore, 0.5, totalRatedGames(b), room.getProblemRating());

        a.setDuelRating(aAfter);
        a.setDuelDraws(safe(a.getDuelDraws()) + 1);
        b.setDuelRating(bAfter);
        b.setDuelDraws(safe(b.getDuelDraws()) + 1);
        userRepository.save(a);
        userRepository.save(b);

        recordHistory(room, a, aAfter, aAfter - aBefore);
        recordHistory(room, b, bAfter, bAfter - bBefore);

        recordMatch(room, a, b, "DRAW", aBefore, aAfter, null);
        recordMatch(room, b, a, "DRAW", bBefore, bAfter, null);
    }

    // ------------------------------------------------------------------
    // ELO math
    // ------------------------------------------------------------------

    /**
     * Computes a single player's new rating against one opponent.
     *
     * @param actual 1.0 win, 0.5 draw, 0.0 loss.
     */
    int newRating(int rating, int opponentRating, double actual, int totalGames, int problemRating) {
        double expected = 1.0 / (1.0 + Math.pow(10, (opponentRating - rating) / 400.0));
        double k = baseK(totalGames) * problemModifier(rating, opponentRating, problemRating);
        int updated = (int) Math.round(rating + k * (actual - expected));
        return Math.max(RATING_FLOOR, updated);
    }

    /** Dynamic K-factor based on the player's total rated games (spec §9). */
    private static int baseK(int totalGames) {
        return totalGames < 10 ? 64 : totalGames < 30 ? 32 : 16;
    }

    /**
     * Scales K by problem difficulty relative to the two players' average
     * rating, clamped to [0.5, 1.5] (spec §9, "Problem Rating Bonus").
     */
    private static double problemModifier(int rating, int opponentRating, int problemRating) {
        double avgDuelRating = (rating + opponentRating) / 2.0;
        double problemDifficulty = (problemRating - avgDuelRating) / 400.0;
        return Math.max(0.5, Math.min(1.5, 1.0 + problemDifficulty * 0.2));
    }

    // ------------------------------------------------------------------
    // Persistence helpers
    // ------------------------------------------------------------------

    private void recordHistory(DuelRoom room, User user, int rating, int delta) {
        ratingHistoryRepository.save(RatingHistory.builder()
                .userId(user.getId())
                .duelId(room.getId())
                .rating(rating)
                .delta(delta)
                .build());
    }

    private void recordMatch(
            DuelRoom room, User user, User opponent, String result,
            int ratingBefore, int ratingAfter, Long durationMs) {
        matchHistoryRepository.save(MatchHistory.builder()
                .roomId(room.getId())
                .userId(user.getId())
                .opponentIds(new java.util.UUID[] {opponent.getId()})
                .result(result)
                .problemId(room.getProblemId())
                .problemRating(room.getProblemRating())
                .problemUrl(room.getProblemUrl())
                .duelType(room.getRoomType())
                .durationMs(durationMs)
                .ratingBefore(ratingBefore)
                .ratingAfter(ratingAfter)
                .build());
    }

    private static int ratingOf(User u) {
        return u.getDuelRating() == null ? 1200 : u.getDuelRating();
    }

    private static int totalRatedGames(User u) {
        return safe(u.getDuelWins()) + safe(u.getDuelLosses()) + safe(u.getDuelDraws());
    }

    private static int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
