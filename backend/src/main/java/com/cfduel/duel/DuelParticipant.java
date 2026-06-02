package com.cfduel.duel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * JPA entity mapped to the {@code duel_participants} table (spec §5). Columns
 * mirror the migration in {@code V1__init_schema.sql} exactly.
 */
@Entity
@Table(name = "duel_participants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DuelParticipant {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "team")
    private Integer team;

    @Column(name = "slot")
    private Integer slot;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @PrePersist
    void onCreate() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
        if (status == null) {
            status = "ACTIVE";
        }
    }
}
