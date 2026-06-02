package com.cfduel.duel.dto;

/**
 * Body for {@code POST /api/duels/{roomCode}/join}. Both fields are optional and
 * act as hints; the service assigns final team/slot when absent.
 */
public record JoinDuelRequest(Integer team, Integer slot) {
}
