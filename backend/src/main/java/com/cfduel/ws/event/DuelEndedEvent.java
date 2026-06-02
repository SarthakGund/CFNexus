package com.cfduel.ws.event;

import java.util.List;
import java.util.UUID;

public record DuelEndedEvent(
        UUID winnerId, List<UUID> loserIds, String resultType, Long solveDurationMs)
        implements DuelEvent {

    @Override
    public String eventType() {
        return "DUEL_ENDED";
    }
}
