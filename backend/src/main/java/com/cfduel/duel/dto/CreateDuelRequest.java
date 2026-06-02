package com.cfduel.duel.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Body for {@code POST /api/duels/create}.
 *
 * @param type          one of {RATED_1V1, UNRATED_TEAM, UNRATED_FFA}
 * @param problemRating target problem rating, 800..3500
 */
public record CreateDuelRequest(
        @NotNull
        @Pattern(regexp = "RATED_1V1|UNRATED_TEAM|UNRATED_FFA",
                message = "type must be one of RATED_1V1, UNRATED_TEAM, UNRATED_FFA")
        String type,

        @Min(800)
        @Max(3500)
        int problemRating) {
}
