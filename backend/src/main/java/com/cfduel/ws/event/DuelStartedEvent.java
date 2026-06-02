package com.cfduel.ws.event;

import java.time.Instant;

public record DuelStartedEvent(
        String problemId, String name, String url, Integer rating, Instant startedAt)
        implements DuelEvent {

    @Override
    public String eventType() {
        return "DUEL_STARTED";
    }
}
