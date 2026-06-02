package com.cfduel.cf.dto;

/**
 * A single rating change entry as returned by {@code /user.rating}.
 * Reserved for Phase 3 use.
 *
 * @param contestId             contest id where the rating change occurred.
 * @param ratingUpdateTimeSeconds time of the rating update as Unix epoch seconds.
 * @param newRating             rating after the contest.
 * @param oldRating             rating before the contest.
 */
public record CfRatingChange(Integer contestId, Long ratingUpdateTimeSeconds, Integer newRating, Integer oldRating) {
}
