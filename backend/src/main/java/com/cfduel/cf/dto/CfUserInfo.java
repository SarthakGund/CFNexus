package com.cfduel.cf.dto;

/**
 * Minimal projection of a Codeforces user as returned by {@code /user.info}.
 *
 * @param handle    Codeforces handle.
 * @param rating    current rating (may be null for unrated users).
 * @param maxRating maximum rating (may be null for unrated users).
 * @param rank      current rank title (may be null).
 * @param maxRank   maximum rank title (may be null).
 * @param avatar    avatar image URL (may be null).
 */
public record CfUserInfo(String handle, Integer rating, Integer maxRating, String rank, String maxRank, String avatar) {
}
