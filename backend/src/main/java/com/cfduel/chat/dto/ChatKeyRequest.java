package com.cfduel.chat.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/duels/{roomCode}/chat-key}: the caller's base64 SPKI public key. */
public record ChatKeyRequest(@NotBlank String publicKeyB64) {
}
