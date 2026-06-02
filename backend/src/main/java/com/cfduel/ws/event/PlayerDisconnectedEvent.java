package com.cfduel.ws.event;

import java.util.UUID;

public record PlayerDisconnectedEvent(UUID userId, int gracePeriodSeconds) implements DuelEvent {

    @Override
    public String eventType() {
        return "PLAYER_DISCONNECTED";
    }
}
