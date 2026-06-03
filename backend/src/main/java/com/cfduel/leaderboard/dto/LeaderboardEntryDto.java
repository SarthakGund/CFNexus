package com.cfduel.leaderboard.dto;

import com.cfduel.user.User;

/**
 * One ranked row in a leaderboard response (spec §13). {@code rank} is 1-based
 * and computed from the page offset; {@code value} is the metric for the
 * requested category so the frontend can render it without knowing which column
 * was sorted on.
 */
public record LeaderboardEntryDto(
        int rank,
        String handle,
        String avatarUrl,
        int duelRating,
        int duelWins,
        int duelLosses,
        int duelDraws,
        int unratedWins,
        int currentStreak,
        Long fastestSolveMs,
        long value) {

    public static LeaderboardEntryDto from(User u, int rank, long value) {
        return new LeaderboardEntryDto(
                rank,
                u.getCfHandle(),
                u.getAvatarUrl(),
                u.getDuelRating() == null ? 0 : u.getDuelRating(),
                u.getDuelWins() == null ? 0 : u.getDuelWins(),
                u.getDuelLosses() == null ? 0 : u.getDuelLosses(),
                u.getDuelDraws() == null ? 0 : u.getDuelDraws(),
                u.getUnratedWins() == null ? 0 : u.getUnratedWins(),
                u.getCurrentStreak() == null ? 0 : u.getCurrentStreak(),
                u.getFastestSolveMs(),
                value);
    }
}
