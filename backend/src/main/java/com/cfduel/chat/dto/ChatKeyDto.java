package com.cfduel.chat.dto;

import java.util.UUID;

/** A single participant's exported public key for a room (spec §11). */
public record ChatKeyDto(UUID userId, String handle, String publicKeyB64) {
}
