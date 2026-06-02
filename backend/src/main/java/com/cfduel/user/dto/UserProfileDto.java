package com.cfduel.user.dto;

import com.cfduel.user.User;
import java.time.Instant;
import java.util.UUID;

/**
 * Public-facing user profile projection returned by the user/auth endpoints.
 * Intentionally omits no fields here (all are public-safe), but keeps the API
 * decoupled from the JPA entity.
 */
public record UserProfileDto(
        UUID id,
        String cfHandle,
        Long cfUserId,
        Integer cfRating,
        Integer cfMaxRating,
        String cfRank,
        String cfMaxRank,
        String avatarUrl,
        Integer duelRating,
        Integer duelWins,
        Integer duelLosses,
        Integer duelDraws,
        Integer unratedWins,
        Integer currentStreak,
        Integer maxStreak,
        Long fastestSolveMs,
        String bio,
        String favoriteLang,
        Boolean isOnline,
        Instant lastSeen,
        Instant createdAt) {

    public static UserProfileDto from(User u) {
        return new UserProfileDto(
                u.getId(),
                u.getCfHandle(),
                u.getCfUserId(),
                u.getCfRating(),
                u.getCfMaxRating(),
                u.getCfRank(),
                u.getCfMaxRank(),
                u.getAvatarUrl(),
                u.getDuelRating(),
                u.getDuelWins(),
                u.getDuelLosses(),
                u.getDuelDraws(),
                u.getUnratedWins(),
                u.getCurrentStreak(),
                u.getMaxStreak(),
                u.getFastestSolveMs(),
                u.getBio(),
                u.getFavoriteLang(),
                u.getIsOnline(),
                u.getLastSeen(),
                u.getCreatedAt());
    }
}
