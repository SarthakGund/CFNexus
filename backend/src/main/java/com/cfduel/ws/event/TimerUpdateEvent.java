package com.cfduel.ws.event;

public record TimerUpdateEvent(long elapsedMs) implements DuelEvent {

    @Override
    public String eventType() {
        return "TIMER_UPDATE";
    }
}
