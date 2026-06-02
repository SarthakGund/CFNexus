package com.cfduel.ws.event;

import java.util.UUID;

public record PlayerJoinedEvent(UUID userId, String handle, Integer team, Integer slot)
        implements DuelEvent {

    @Override
    public String eventType() {
        return "PLAYER_JOINED";
    }
}
