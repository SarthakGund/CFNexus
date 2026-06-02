package com.cfduel.ws.event;

/**
 * Sealed event hierarchy broadcast over STOMP to {@code /topic/duel/{roomCode}}.
 *
 * <p>Every permitted type is a Java {@code record} exposing an {@code eventType}
 * bean property (via {@link #eventType()}). Jackson therefore serializes a
 * stable {@code "eventType"} discriminator field for each event automatically.
 */
public sealed interface DuelEvent
        permits RoomStateEvent,
                PlayerJoinedEvent,
                PlayerLeftEvent,
                DuelStartedEvent,
                DrawOfferedEvent,
                DrawDeclinedEvent,
                DuelEndedEvent,
                PlayerDisconnectedEvent,
                PlayerReconnectedEvent,
                TimerUpdateEvent {

    String eventType();
}
