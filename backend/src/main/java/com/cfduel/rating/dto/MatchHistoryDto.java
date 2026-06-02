package com.cfduel.rating.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One row of the profile match-history table. Opponent handles are resolved
 * server-side from {@code opponent_ids} so the client can link to profiles.
 */
public record MatchHistoryDto(
        UUID roomId,
        String result,
        String problemId,
        Integer problemRating,
        String problemUrl,
        String duelType,
        Long durationMs,
        Integer ratingBefore,
        Integer ratingAfter,
        Integer ratingDelta,
        List<String> opponentHandles,
        Instant playedAt) {
}
