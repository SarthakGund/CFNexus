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
 * JPA entity mapped to the {@code duel_rooms} table (spec §5). Columns mirror
 * the migration in {@code V1__init_schema.sql} exactly.
 *
 * <p>{@code status} is one of {WAITING, IN_PROGRESS, COMPLETED, CANCELLED} and
 * {@code roomType} one of {RATED_1V1, UNRATED_TEAM, UNRATED_FFA}; both stored as
 * plain strings.
 */
@Entity
@Table(name = "duel_rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DuelRoom {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "room_code", nullable = false, unique = true, length = 12)
    private String roomCode;

    @Column(name = "room_type", nullable = false, length = 20)
    private String roomType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "host_id")
    private UUID hostId;

    @Column(name = "problem_rating", nullable = false)
    private Integer problemRating;

    @Column(name = "problem_id", length = 30)
    private String problemId;

    @Column(name = "problem_url", columnDefinition = "text")
    private String problemUrl;

    @Column(name = "winner_team")
    private Integer winnerTeam;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "WAITING";
        }
    }
}
