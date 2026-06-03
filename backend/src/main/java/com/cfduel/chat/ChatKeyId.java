package com.cfduel.chat;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link ChatKey} — mirrors the {@code chat_keys}
 * table's {@code PRIMARY KEY(room_id, user_id)} from {@code V1__init_schema.sql}.
 */
public class ChatKeyId implements Serializable {

    private UUID roomId;
    private UUID userId;

    public ChatKeyId() {
    }

    public ChatKeyId(UUID roomId, UUID userId) {
        this.roomId = roomId;
        this.userId = userId;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatKeyId that)) {
            return false;
        }
        return Objects.equals(roomId, that.roomId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomId, userId);
    }
}
