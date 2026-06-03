package com.cfduel.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity mapped to the existing {@code chat_keys} table (spec §11). Columns
 * mirror the migration in {@code V1__init_schema.sql} exactly — composite PK of
 * {@code (room_id, user_id)} expressed via {@link ChatKeyId}.
 *
 * <p>Stores a participant's <em>public</em> ECDH key only; private keys never
 * leave the client.
 */
@Entity
@Table(name = "chat_keys")
@IdClass(ChatKeyId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatKey {

    @Id
    @Column(name = "room_id")
    private UUID roomId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "public_key_b64", columnDefinition = "text", nullable = false)
    private String publicKeyB64;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
