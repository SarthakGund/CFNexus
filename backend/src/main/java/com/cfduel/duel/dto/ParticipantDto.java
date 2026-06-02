package com.cfduel.duel.dto;

import java.util.UUID;

/** A single duel participant, joined with its user's handle/avatar. */
public record ParticipantDto(
        UUID userId,
        String handle,
        String avatarUrl,
        Integer team,
        Integer slot,
        String status) {
}
