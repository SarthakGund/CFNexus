package com.cfduel.achievement;

import com.cfduel.duel.DuelParticipant;
import com.cfduel.duel.DuelParticipantRepository;
import com.cfduel.duel.event.DuelCompletedEvent;
import com.cfduel.user.User;
import com.cfduel.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates the user statistics that {@code RatingService} does not own
 * (current_streak / max_streak / fastest_solve_ms) and grants achievements
 * (spec §13). Invoked by {@link AchievementChecker} after a duel result has
 * been committed.
 *
 * <p>Granting is idempotent: a user_achievements row is only inserted when one
 * does not already exist for the (user, achievement) pair.
 *
 * <p>Condition semantics (from {@code V2__seed_achievements.sql}):
 * <ul>
 *   <li>WINS          — duel_wins &gt;= value</li>
 *   <li>STREAK        — max_streak &gt;= value (covers current too)</li>
 *   <li>RATING        — duel_rating &gt;= value</li>
 *   <li>FAST_SOLVE_MS — fastest_solve_ms &lt;= value</li>
 *   <li>CLEAN_STREAK  — current_streak &gt;= value (see simplification note)</li>
 *   <li>HARD_PROBLEM  — solved a problem rated value+ above own cf_rating</li>
 *   <li>TEAM_WIN      — won a team duel of value-per-side players (4v4)</li>
 * </ul>
 *
 * <p><b>CLEAN_STREAK simplification:</b> the platform only awards a duel win
 * via SOLVE, RESIGN or DISCONNECT (never a "win" recorded against the streak
 * after a draw — a draw resets current_streak to 0 below). Because
 * current_streak is reset on any non-win result, a current_streak of N already
 * means N consecutive decisive wins with no draw in between, so we evaluate
 * CLEAN_STREAK against current_streak rather than tracking a separate counter.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AchievementService {

    private static final String TYPE_UNRATED_TEAM = "UNRATED_TEAM";

    private final UserRepository userRepository;
    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final DuelParticipantRepository participantRepository;

    /**
     * Applies post-duel stat updates and evaluates achievements for everyone
     * affected by the result. Runs in its own transaction (called from the
     * AFTER_COMMIT listener, so it must open a fresh one).
     */
    @Transactional
    public void onDuelCompleted(DuelCompletedEvent event) {
        boolean draw = event.winnerId() == null;
        List<UUID> winnerIds = resolveWinners(event);

        // 1. Update streak / fastest-solve stats that RatingService doesn't touch.
        boolean solve = "SOLVE".equals(event.resultType());
        for (UUID winnerId : winnerIds) {
            User winner = userRepository.findById(winnerId).orElse(null);
            if (winner == null) {
                continue;
            }
            int current = safe(winner.getCurrentStreak()) + 1;
            winner.setCurrentStreak(current);
            if (current > safe(winner.getMaxStreak())) {
                winner.setMaxStreak(current);
            }
            if (solve && event.solveDurationMs() != null) {
                Long best = winner.getFastestSolveMs();
                if (best == null || event.solveDurationMs() < best) {
                    winner.setFastestSolveMs(event.solveDurationMs());
                }
            }
            userRepository.save(winner);
        }

        // Losers (and both players on a draw) break their win streak.
        List<UUID> streakBreakers = new ArrayList<>(event.loserIds());
        if (draw && event.winnerId() != null) {
            streakBreakers.add(event.winnerId());
        }
        for (UUID loserId : streakBreakers) {
            userRepository.findById(loserId).ifPresent(loser -> {
                loser.setCurrentStreak(0);
                userRepository.save(loser);
            });
        }

        // 2. Evaluate achievements for every affected user.
        List<Achievement> catalogue = achievementRepository.findAll();
        for (UUID winnerId : winnerIds) {
            evaluateForUser(winnerId, catalogue, event, true);
        }
        for (UUID loserId : event.loserIds()) {
            // Losers can still cross RATING / WINS thresholds is impossible on a
            // loss, but re-evaluating is cheap and keeps the grant idempotent.
            evaluateForUser(loserId, catalogue, event, false);
        }
    }

    /** Resolves the set of users credited with the win (team duels credit the whole side). */
    private List<UUID> resolveWinners(DuelCompletedEvent event) {
        List<UUID> winners = new ArrayList<>();
        if (event.winnerId() == null) {
            return winners; // draw — nobody wins
        }
        if (TYPE_UNRATED_TEAM.equals(event.roomType()) && event.winnerTeam() != null) {
            for (DuelParticipant p : participantRepository.findByRoomId(event.roomId())) {
                if (event.winnerTeam().equals(p.getTeam())) {
                    winners.add(p.getUserId());
                }
            }
            if (!winners.isEmpty()) {
                return winners;
            }
        }
        winners.add(event.winnerId());
        return winners;
    }

    private void evaluateForUser(
            UUID userId, List<Achievement> catalogue, DuelCompletedEvent event, boolean isWinner) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        for (Achievement achievement : catalogue) {
            if (matches(achievement, user, event, isWinner)
                    && !userAchievementRepository.existsByUserIdAndAchievementId(
                            userId, achievement.getId())) {
                userAchievementRepository.save(UserAchievement.builder()
                        .userId(userId)
                        .achievementId(achievement.getId())
                        .build());
                log.info("Granted achievement {} to user {}", achievement.getCode(), userId);
            }
        }
    }

    /** Evaluates one achievement condition against a user + duel context. */
    private boolean matches(
            Achievement achievement, User user, DuelCompletedEvent event, boolean isWinner) {
        String type = achievement.getConditionType();
        int value = achievement.getConditionValue() == null ? 0 : achievement.getConditionValue();
        if (type == null) {
            return false;
        }
        return switch (type) {
            case "WINS" -> safe(user.getDuelWins()) >= value;
            case "STREAK" -> safe(user.getMaxStreak()) >= value;
            case "RATING" -> user.getDuelRating() != null && user.getDuelRating() >= value;
            case "CLEAN_STREAK" -> safe(user.getCurrentStreak()) >= value;
            case "FAST_SOLVE_MS" -> user.getFastestSolveMs() != null
                    && user.getFastestSolveMs() <= value;
            case "HARD_PROBLEM" -> isWinner
                    && "SOLVE".equals(event.resultType())
                    && event.problemRating() != null
                    && user.getCfRating() != null
                    && event.problemRating() >= user.getCfRating() + value;
            case "TEAM_WIN" -> isWinner
                    && TYPE_UNRATED_TEAM.equals(event.roomType())
                    && teamSize(event, user.getId()) >= value
                    && opposingTeamSize(event, user.getId()) >= value;
            default -> false;
        };
    }

    /** Number of players on the given user's team in the completed room. */
    private long teamSize(DuelCompletedEvent event, UUID userId) {
        List<DuelParticipant> participants = participantRepository.findByRoomId(event.roomId());
        Integer team = participants.stream()
                .filter(p -> userId.equals(p.getUserId()))
                .map(DuelParticipant::getTeam)
                .findFirst()
                .orElse(null);
        if (team == null) {
            return 0;
        }
        return participants.stream().filter(p -> team.equals(p.getTeam())).count();
    }

    /** Number of players on the team opposing the given user. */
    private long opposingTeamSize(DuelCompletedEvent event, UUID userId) {
        List<DuelParticipant> participants = participantRepository.findByRoomId(event.roomId());
        Integer team = participants.stream()
                .filter(p -> userId.equals(p.getUserId()))
                .map(DuelParticipant::getTeam)
                .findFirst()
                .orElse(null);
        if (team == null) {
            return 0;
        }
        return participants.stream()
                .filter(p -> p.getTeam() != null && !team.equals(p.getTeam()))
                .count();
    }

    private static int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
