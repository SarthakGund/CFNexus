package com.cfduel.cf.dto;

/**
 * A single Codeforces submission as returned by {@code /user.status} (spec §10).
 * The nested {@code problem.{contestId,index}} fields are flattened here.
 *
 * @param id                  submission id.
 * @param verdict             submission verdict, e.g. {@code "OK"} (may be null while testing).
 * @param contestId           contest id of the submitted problem (may be null).
 * @param index               problem index of the submitted problem.
 * @param creationTimeSeconds submission time as Unix epoch seconds.
 */
public record CfSubmission(Long id, String verdict, Integer contestId, String index, Long creationTimeSeconds) {

    /** Stable key identifying the submitted problem, e.g. {@code "1234-A"}. */
    public String problemKey() {
        return contestId + "-" + index;
    }
}
