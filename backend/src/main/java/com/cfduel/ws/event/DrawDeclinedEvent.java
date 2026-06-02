package com.cfduel.ws.event;

import java.util.UUID;

public record DrawDeclinedEvent(UUID byUserId) implements DuelEvent {

    @Override
    public String eventType() {
        return "DRAW_DECLINED";
    }
}
