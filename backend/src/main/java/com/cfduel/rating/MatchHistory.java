package com.cfduel.rating;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity mapped to the {@code match_history} table (spec §5). One row per
 * player per completed duel, denormalised for fast profile rendering. The
 * {@code opponent_ids} column is a Postgres {@code uuid[]} array.
 */
@Entity
@Table(name = "match_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchHistory {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "user_id")
    private UUID userId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "opponent_ids", columnDefinition = "uuid[]")
    private UUID[] opponentIds;

    @Column(name = "result", length = 20)
    private String result;

    @Column(name = "problem_id", length = 30)
    private String problemId;

    @Column(name = "problem_rating")
    private Integer problemRating;

    @Column(name = "problem_url", columnDefinition = "text")
    private String problemUrl;

    @Column(name = "duel_type", length = 20)
    private String duelType;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "rating_before")
    private Integer ratingBefore;

    @Column(name = "rating_after")
    private Integer ratingAfter;

    @Column(name = "played_at", updatable = false)
    private Instant playedAt;

    @PrePersist
    void onCreate() {
        if (playedAt == null) {
            playedAt = Instant.now();
        }
    }
}
