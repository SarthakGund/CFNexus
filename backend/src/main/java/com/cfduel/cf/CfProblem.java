package com.cfduel.cf;

/**
 * A Codeforces problem candidate used for duel problem selection (spec §10).
 *
 * <p>Lives in {@code com.cfduel.cf} (not {@code cf.dto}) because the integrated
 * {@code com.cfduel.duel.DuelService} imports it from this package.
 *
 * @param contestId   Codeforces contest id (may be null for some problems).
 * @param index       problem index within the contest (e.g. {@code "A"}).
 * @param name        human-readable problem name.
 * @param rating      problem difficulty rating (may be null if unrated).
 * @param solvedCount number of users who solved the problem (from problemStatistics).
 */
public record CfProblem(Integer contestId, String index, String name, Integer rating, Long solvedCount) {

    /** Stable key identifying a problem, e.g. {@code "1234-A"}. */
    public String problemKey() {
        return contestId + "-" + index;
    }

    /** Public problemset URL for the problem. */
    public String url() {
        return "https://codeforces.com/problemset/problem/" + contestId + "/" + index;
    }
}
