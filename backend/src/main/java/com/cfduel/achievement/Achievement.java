package com.cfduel.achievement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * JPA entity mapped to the {@code achievements} catalogue table (spec §13).
 * Rows are seeded by {@code V2__seed_achievements.sql}; the application only
 * reads them. {@code conditionType} is one of {WINS, STREAK, RATING,
 * FAST_SOLVE_MS, CLEAN_STREAK, HARD_PROBLEM, TEAM_WIN} (see that migration's
 * header) and {@code conditionValue} is the numeric threshold.
 */
@Entity
@Table(name = "achievements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Achievement {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "condition_type", length = 30)
    private String conditionType;

    @Column(name = "condition_value")
    private Integer conditionValue;
}
