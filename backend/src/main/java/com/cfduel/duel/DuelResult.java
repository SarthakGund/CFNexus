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
 * JPA entity mapped to the {@code duel_results} table (spec §5). Columns mirror
 * the migration in {@code V1__init_schema.sql} exactly. Rating fields are left
 * null in Phase 2 (ELO is Phase 3).
 */
@Entity
@Table(name = "duel_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DuelResult {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "winner_id")
    private UUID winnerId;

    @Column(name = "loser_id")
    private UUID loserId;

    @Column(name = "result_type", nullable = false, length = 20)
    private String resultType;

    @Column(name = "winner_rating_before")
    private Integer winnerRatingBefore;

    @Column(name = "winner_rating_after")
    private Integer winnerRatingAfter;

    @Column(name = "loser_rating_before")
    private Integer loserRatingBefore;

    @Column(name = "loser_rating_after")
    private Integer loserRatingAfter;

    @Column(name = "rating_delta")
    private Integer ratingDelta;

    @Column(name = "solve_duration_ms")
    private Long solveDurationMs;

    @Column(name = "problem_id", length = 30)
    private String problemId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
