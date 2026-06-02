package com.cfduel.duel.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full state snapshot of a duel room. {@code problem} is null until the duel has
 * started.
 */
public record RoomStateDto(
        String roomCode,
        String type,
        String status,
        UUID hostId,
        Integer problemRating,
        ProblemDto problem,
        List<ParticipantDto> participants,
        Instant startedAt) {
}
