package com.cfduel.rating.dto;

import com.cfduel.rating.RatingHistory;
import java.time.Instant;
import java.util.UUID;

/** A single duel-rating data point for the profile rating graph. */
public record RatingPointDto(UUID duelId, Integer rating, Integer delta, Instant recordedAt) {

    public static RatingPointDto from(RatingHistory h) {
        return new RatingPointDto(h.getDuelId(), h.getRating(), h.getDelta(), h.getRecordedAt());
    }
}
