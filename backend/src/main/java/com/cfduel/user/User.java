package com.cfduel.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
 * JPA entity mapped to the {@code users} table (spec §5). Columns mirror the
 * migration in {@code V1__init_schema.sql} exactly.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "cf_handle", nullable = false, unique = true, length = 50)
    private String cfHandle;

    @Column(name = "cf_user_id", nullable = false, unique = true)
    private Long cfUserId;

    @Column(name = "cf_rating")
    private Integer cfRating;

    @Column(name = "cf_max_rating")
    private Integer cfMaxRating;

    @Column(name = "cf_rank", length = 30)
    private String cfRank;

    @Column(name = "cf_max_rank", length = 30)
    private String cfMaxRank;

    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;

    @Column(name = "duel_rating")
    private Integer duelRating;

    @Column(name = "duel_wins")
    private Integer duelWins;

    @Column(name = "duel_losses")
    private Integer duelLosses;

    @Column(name = "duel_draws")
    private Integer duelDraws;

    @Column(name = "unrated_wins")
    private Integer unratedWins;

    @Column(name = "current_streak")
    private Integer currentStreak;

    @Column(name = "max_streak")
    private Integer maxStreak;

    @Column(name = "fastest_solve_ms")
    private Long fastestSolveMs;

    @Column(name = "bio", columnDefinition = "text")
    private String bio;

    @Column(name = "favorite_lang", length = 20)
    private String favoriteLang;

    @Column(name = "is_online")
    private Boolean isOnline;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        applyDefaults();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Backfills the column defaults from spec §5 when not explicitly set. */
    private void applyDefaults() {
        if (cfRating == null) {
            cfRating = 0;
        }
        if (cfMaxRating == null) {
            cfMaxRating = 0;
        }
        if (duelRating == null) {
            duelRating = 1200;
        }
        if (duelWins == null) {
            duelWins = 0;
        }
        if (duelLosses == null) {
            duelLosses = 0;
        }
        if (duelDraws == null) {
            duelDraws = 0;
        }
        if (unratedWins == null) {
            unratedWins = 0;
        }
        if (currentStreak == null) {
            currentStreak = 0;
        }
        if (maxStreak == null) {
            maxStreak = 0;
        }
        if (isOnline == null) {
            isOnline = false;
        }
    }
}
