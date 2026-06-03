package com.cfduel.achievement;

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
 * JPA entity mapped to the {@code user_achievements} join table (spec §13).
 * A row records that a user has earned a given achievement; the
 * {@code (user_id, achievement_id)} pair is unique.
 */
@Entity
@Table(name = "user_achievements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAchievement {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "achievement_id")
    private UUID achievementId;

    @Column(name = "earned_at")
    private Instant earnedAt;

    @PrePersist
    void onCreate() {
        if (earnedAt == null) {
            earnedAt = Instant.now();
        }
    }
}
