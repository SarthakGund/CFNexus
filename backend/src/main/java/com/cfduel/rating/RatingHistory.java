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
import org.hibernate.annotations.UuidGenerator;

/**
 * JPA entity mapped to the {@code rating_history} table (spec §5). One row is
 * inserted per player per rated duel, capturing the player's new duel rating and
 * the delta applied. Drives the duel-rating graph on the profile page.
 */
@Entity
@Table(name = "rating_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RatingHistory {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "duel_id")
    private UUID duelId;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "delta", nullable = false)
    private Integer delta;

    @Column(name = "recorded_at", updatable = false)
    private Instant recordedAt;

    @PrePersist
    void onCreate() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }
}
