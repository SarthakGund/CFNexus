package com.cfduel.duel.dto;

/** Response for {@code POST /api/duels/create}. */
public record CreateRoomResponse(String roomCode, String inviteUrl) {
}
