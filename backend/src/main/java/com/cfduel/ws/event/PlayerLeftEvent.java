package com.cfduel.ws.event;

import java.util.UUID;

public record PlayerLeftEvent(UUID userId) implements DuelEvent {

    @Override
    public String eventType() {
        return "PLAYER_LEFT";
    }
}
