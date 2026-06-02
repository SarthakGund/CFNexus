package com.cfduel.ws.event;

/**
 * Full snapshot of the room. {@code room} is the RoomStateDto (typed as
 * {@link Object} to avoid a cross-package compile dependency on Agent A/C code).
 */
public record RoomStateEvent(Object room) implements DuelEvent {

    @Override
    public String eventType() {
        return "ROOM_STATE";
    }
}
