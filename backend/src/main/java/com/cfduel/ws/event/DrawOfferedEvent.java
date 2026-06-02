package com.cfduel.ws.event;

import java.util.UUID;

public record DrawOfferedEvent(UUID byUserId) implements DuelEvent {

    @Override
    public String eventType() {
        return "DRAW_OFFERED";
    }
}
